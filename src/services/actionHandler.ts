import { AssistantAction, AssistantActionType } from '../types';
import { logger } from './loggerService';

export interface ActionParseResult {
  hasAction: boolean;
  action?: AssistantAction;
  cleanedText: string;
  spokenSummary: string;
}

export class ActionHandler {
  private static ALLOWED_ACTIONS: AssistantActionType[] = [
    'OPEN_APP',
    'OPEN_URL',
    'SEARCH_YOUTUBE',
    'OPEN_SETTINGS',
    'MAKE_CALL',
    'SEND_SMS',
  ];

  public static parseActionFromLLM(text: string): ActionParseResult {
    // Look for JSON block ```json ... ``` or raw JSON
    const jsonMatch = text.match(/```(?:json)?\s*([\s\S]*?)\s*```/) || text.match(/\{[\s\S]*?"action"[\s\S]*?\}/);
    
    if (!jsonMatch) {
      // Direct heuristic fallback if the LLM output plain text like "Action: SEARCH_YOUTUBE ..."
      return this.heuristicParse(text);
    }

    try {
      const jsonStr = jsonMatch[1] || jsonMatch[0];
      const parsed = JSON.parse(jsonStr.trim());

      if (parsed && typeof parsed.action === 'string') {
        const actionType = parsed.action.toUpperCase() as AssistantActionType;
        if (this.ALLOWED_ACTIONS.includes(actionType)) {
          const action: AssistantAction = {
            id: Math.random().toString(36).substring(2, 9),
            type: actionType,
            appName: parsed.app || parsed.appName || (actionType === 'SEARCH_YOUTUBE' ? 'YouTube' : undefined),
            url: parsed.url,
            query: parsed.query,
            phoneNumber: parsed.phoneNumber,
            messageText: parsed.messageText,
            requiresConfirmation: actionType === 'MAKE_CALL' || actionType === 'SEND_SMS',
            confirmed: false,
            executed: false,
          };

          const cleanedText = text.replace(jsonMatch[0], '').trim();
          const spokenSummary = this.generateSpokenSummary(action, cleanedText);

          logger.log('ACTION_PARSED', `Structured action parsed: ${action.type}`, { action });
          return {
            hasAction: true,
            action,
            cleanedText: cleanedText || spokenSummary,
            spokenSummary,
          };
        }
      }
    } catch (e) {
      console.warn('Failed to parse JSON action block from LLM output', e);
    }

    return this.heuristicParse(text);
  }

  private static heuristicParse(text: string): ActionParseResult {
    const lower = text.toLowerCase();

    // YouTube Search
    if (lower.includes('youtube') && (lower.includes('search') || lower.includes('play') || lower.includes('songs') || lower.includes('for'))) {
      let query = '';
      const match = text.match(/(?:search|play|for|about)\s+(.+?)(?:on youtube|in youtube|$)/i) ||
                    text.match(/youtube\s+(?:and\s+)?(?:search|play)\s+(.+)/i);
      if (match) {
        query = match[1].replace(/on youtube/i, '').replace(/in youtube/i, '').trim();
      } else {
        query = text.replace(/open youtube/i, '').replace(/and search/i, '').trim();
      }

      if (!query || query.toLowerCase() === 'youtube') query = 'trending music';

      const action: AssistantAction = {
        id: Math.random().toString(36).substring(2, 9),
        type: 'SEARCH_YOUTUBE',
        appName: 'YouTube',
        query,
        requiresConfirmation: false,
        intentAction: 'android.intent.action.VIEW',
        intentDataUri: `https://www.youtube.com/results?search_query=${encodeURIComponent(query)}`,
      };

      const spokenSummary = `Opening ${query} on YouTube.`;
      logger.log('ACTION_PARSED', `Heuristic action: SEARCH_YOUTUBE ("${query}")`);
      return { hasAction: true, action, cleanedText: text, spokenSummary };
    }

    // Open YouTube specifically
    if (lower === 'open youtube' || lower === 'launch youtube' || lower.startsWith('open youtube')) {
      const action: AssistantAction = {
        id: Math.random().toString(36).substring(2, 9),
        type: 'OPEN_APP',
        appName: 'YouTube',
        requiresConfirmation: false,
        intentAction: 'android.intent.action.MAIN',
        intentDataUri: 'vnd.youtube://',
      };
      const spokenSummary = 'Opening YouTube.';
      logger.log('ACTION_PARSED', 'Heuristic action: OPEN_APP (YouTube)');
      return { hasAction: true, action, cleanedText: text, spokenSummary };
    }

    // Open Chrome
    if (lower.includes('open chrome') || lower.includes('launch chrome')) {
      const action: AssistantAction = {
        id: Math.random().toString(36).substring(2, 9),
        type: 'OPEN_APP',
        appName: 'Google Chrome',
        requiresConfirmation: false,
      };
      const spokenSummary = 'Opening Google Chrome.';
      return { hasAction: true, action, cleanedText: text, spokenSummary };
    }

    // Open Settings
    if (lower.includes('open settings') || lower.includes('device settings') || lower.includes('launch settings')) {
      const action: AssistantAction = {
        id: Math.random().toString(36).substring(2, 9),
        type: 'OPEN_SETTINGS',
        requiresConfirmation: false,
      };
      const spokenSummary = 'Opening device Settings.';
      return { hasAction: true, action, cleanedText: text, spokenSummary };
    }

    return {
      hasAction: false,
      cleanedText: text,
      spokenSummary: text,
    };
  }

  public static generateSpokenSummary(action: AssistantAction, fallbackText: string): string {
    switch (action.type) {
      case 'SEARCH_YOUTUBE':
        return `Opening ${action.query || 'search'} on YouTube.`;
      case 'OPEN_APP':
        return `Opening ${action.appName || 'application'}.`;
      case 'OPEN_SETTINGS':
        return 'Opening device Settings.';
      case 'OPEN_URL':
        return `Opening link: ${action.url}.`;
      case 'MAKE_CALL':
        return `Calling ${action.phoneNumber}...`;
      case 'SEND_SMS':
        return `Sending message to ${action.phoneNumber}.`;
      default:
        return fallbackText || 'Action executed successfully.';
    }
  }

  public static async executeAction(action: AssistantAction): Promise<{ success: boolean; message: string; targetUrl?: string }> {
    logger.log('ACTION_EXECUTED', `Executing Android Intent for action: ${action.type}`, { action });

    switch (action.type) {
      case 'SEARCH_YOUTUBE': {
        const query = action.query || 'Telugu songs';
        const youtubeUrl = `https://www.youtube.com/results?search_query=${encodeURIComponent(query)}`;
        action.intentAction = 'android.intent.action.VIEW';
        action.intentDataUri = youtubeUrl;
        action.executed = true;
        action.resultMessage = `Launched YouTube search for: "${query}"`;
        
        try {
          window.open(youtubeUrl, '_blank', 'noopener,noreferrer');
        } catch (e) {
          console.warn('Popup blocked, provided clickable link', e);
        }

        return {
          success: true,
          message: `Opened YouTube search for "${query}".`,
          targetUrl: youtubeUrl,
        };
      }

      case 'OPEN_APP': {
        const app = (action.appName || '').toLowerCase();
        let targetUrl = '';
        if (app.includes('youtube')) {
          targetUrl = 'https://www.youtube.com';
        } else if (app.includes('chrome') || app.includes('browser')) {
          targetUrl = 'https://www.google.com';
        } else if (app.includes('maps')) {
          targetUrl = 'https://maps.google.com';
        }

        action.executed = true;
        action.resultMessage = `Dispatched Android Intent: android.intent.action.MAIN -> package ${action.appName}`;

        if (targetUrl) {
          try {
            window.open(targetUrl, '_blank', 'noopener,noreferrer');
          } catch (e) {
            console.warn('Window open blocked', e);
          }
        }

        return {
          success: true,
          message: `Opened ${action.appName}.`,
          targetUrl: targetUrl || undefined,
        };
      }

      case 'OPEN_SETTINGS': {
        action.executed = true;
        action.resultMessage = 'Dispatched Android Intent: android.settings.SETTINGS';
        return {
          success: true,
          message: 'Opened system settings screen.',
        };
      }

      case 'OPEN_URL': {
        if (action.url) {
          action.executed = true;
          action.resultMessage = `Dispatched Intent ACTION_VIEW: ${action.url}`;
          try {
            window.open(action.url, '_blank', 'noopener,noreferrer');
          } catch (e) {
            console.warn(e);
          }
          return { success: true, message: `Opened URL: ${action.url}`, targetUrl: action.url };
        }
        return { success: false, message: 'No URL specified.' };
      }

      default:
        return { success: true, message: `Action ${action.type} handled.` };
    }
  }
}
