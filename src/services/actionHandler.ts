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
    'OPEN_YOUTUBE',
    'OPEN_CHROME',
    'OPEN_APP',
    'SEARCH_YOUTUBE',
    'OPEN_SETTINGS',
  ];

  public static parseActionFromLLM(text: string): ActionParseResult {
    // Only one standalone structure is eligible. Prose and code examples stay intact.
    const trimmed = text.trim();
    const jsonMatch = trimmed.match(/^```(?:json)?[ \t]*\r?\n([\s\S]*?)\r?\n```$/i);
    const jsonStr = jsonMatch?.[1]?.trim() ?? trimmed;
    if (jsonStr.length > 4096 || !jsonStr.startsWith('{') || !jsonStr.endsWith('}')) return this.plainResponse(text);

    try {
      const parsed = JSON.parse(jsonStr.trim());

      if (parsed && !Array.isArray(parsed) && typeof parsed.action === 'string' &&
          Object.keys(parsed).every(key => ['action', 'appName', 'query'].includes(key)) &&
          Object.values(parsed).every(value => typeof value === 'string')) {
        const members = jsonStr.match(/"(?:[^"\\]|\\.)*"\s*:\s*"(?:[^"\\]|\\.)*"/g) || [];
        if (members.length !== Object.keys(parsed).length) return this.plainResponse(text);
        const actionType = parsed.action.toUpperCase() as AssistantActionType;
        if (this.ALLOWED_ACTIONS.includes(actionType)) {
          const action: AssistantAction = {
            id: Math.random().toString(36).substring(2, 9),
            type: actionType,
            appName: parsed.appName,
            query: parsed.query,
            requiresConfirmation: false,
            confirmed: false,
            executed: false,
          };

          if (this.validateAction(action)) return this.plainResponse(text);
          const spokenSummary = this.generateSpokenSummary(action, '');

          logger.log('ACTION_PARSED', `Structured action parsed: ${action.type}`, { action });
          return {
            hasAction: true,
            action,
            cleanedText: '',
            spokenSummary,
          };
        }
      }
    } catch {
      // A malformed action is still a displayable model response.
    }

    return this.plainResponse(text);
  }

  public static detectUserRequest(userText: string): AssistantAction | undefined {
    const text = userText.trim().replace(/^(?:(?:please\s+)|(?:(?:can|could|would|will)\s+you\s+(?:please\s+)?))/i, '');
    if (/[\r\n`]/.test(text)) return;
    const search = text.match(/^(?:search\s+(?:on\s+)?youtube\s+for|open\s+youtube\s+and\s+search(?:\s+for)?)\s+(.+)$/i);
    if (search) {
      const action: AssistantAction = { id: crypto.randomUUID(), type: 'SEARCH_YOUTUBE', query: search[1].trim(), requiresConfirmation: false };
      return this.validateAction(action) ? undefined : action;
    }
    const target = text.match(/^(?:open|launch)\s+(?:the\s+)?(.+?)(?:\s+app)?(?:\s+for\s+me)?[.!?]?$/i)?.[1]?.trim();
    if (!target || !/^[\p{L}\p{N}][\p{L}\p{N} ._-]{0,79}$/u.test(target) || target.split(/\s+/).length > 4 ||
        /\b(and|then|with|using|how|why|what|a|an|my|website|webpage|url|file|folder|code|project|terminal|command)\b/i.test(target)) return;
    const aliases: Record<string, AssistantActionType> = {
      youtube: 'OPEN_YOUTUBE', yt: 'OPEN_YOUTUBE', chrome: 'OPEN_CHROME', 'google chrome': 'OPEN_CHROME', browser: 'OPEN_CHROME',
      settings: 'OPEN_SETTINGS', 'android settings': 'OPEN_SETTINGS', 'phone settings': 'OPEN_SETTINGS', 'system settings': 'OPEN_SETTINGS',
    };
    const normalized = target.toLowerCase();
    const type = Object.hasOwn(aliases, normalized) ? aliases[normalized] : 'OPEN_APP';
    const commonApps = new Set(['whatsapp', 'instagram', 'facebook', 'telegram', 'snapchat', 'spotify', 'netflix',
      'discord', 'slack', 'gmail', 'email', 'maps', 'google maps', 'camera', 'photos', 'gallery', 'clock', 'calendar',
      'contacts', 'phone', 'messages', 'calculator', 'play store', 'google play', 'files', 'drive', 'google drive', 'twitter', 'tiktok']);
    if (type === 'OPEN_APP' && !commonApps.has(target.toLowerCase()) &&
        !/\s+app(?:\s+for\s+me)?[.!?]?$/i.test(text) && !/^(?:com|org|net)\.[a-zA-Z_]\w*(?:\.[a-zA-Z_]\w*)+$/.test(target)) return;
    return { id: crypto.randomUUID(), type, appName: type === 'OPEN_APP' ? target : undefined, requiresConfirmation: false };
  }

  public static validateAction(action: AssistantAction): string | null {
    if (!action || !this.ALLOWED_ACTIONS.includes(action.type)) return 'Unsupported device action.';
    if (action.url !== undefined || action.phoneNumber !== undefined || action.messageText !== undefined ||
        (action.type !== 'OPEN_APP' && action.appName !== undefined) ||
        (action.type !== 'SEARCH_YOUTUBE' && action.query !== undefined)) return 'Unexpected action parameters.';
    if ([action.appName, action.query].some(value => value !== undefined && (typeof value !== 'string' || /[\x00-\x1f\x7f-\x9f]/.test(value)))) return 'Invalid action parameters.';
    if (action.type === 'SEARCH_YOUTUBE' && (!action.query?.trim() || action.query.length > 300)) return 'A valid YouTube search query is required.';
    if (action.type === 'OPEN_APP' && (!action.appName || !/^[\p{L}\p{N}][\p{L}\p{N} ._-]{0,79}$/u.test(action.appName))) return 'A valid application name is required.';
    return null;
  }

  private static plainResponse(text: string): ActionParseResult {
    return { hasAction: false, cleanedText: text, spokenSummary: text };
  }

  public static generateSpokenSummary(action: AssistantAction, fallbackText: string): string {
    switch (action.type) {
      case 'OPEN_YOUTUBE':
        return 'Opening YouTube.';
      case 'OPEN_CHROME':
        return 'Opening Google Chrome.';
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

  public static async executeAction(action: AssistantAction, signal?: AbortSignal): Promise<{ success: boolean; message: string; targetUrl?: string }> {
    const invalid = this.validateAction(action);
    if (invalid) return { success: false, message: invalid };
    signal?.throwIfAborted();
    logger.log('ACTION_EXECUTED', `Executing Android Intent for action: ${action.type}`, { action });

    switch (action.type) {
      case 'OPEN_YOUTUBE':
        return this.executeAction({ ...action, type: 'OPEN_APP', appName: 'YouTube' }, signal);
      case 'OPEN_CHROME':
        return this.executeAction({ ...action, type: 'OPEN_APP', appName: 'Google Chrome' }, signal);
      case 'SEARCH_YOUTUBE': {
        const query = action.query!;
        const youtubeUrl = `https://www.youtube.com/results?search_query=${encodeURIComponent(query)}`;
        
        try {
          window.open(youtubeUrl, '_blank', 'noopener,noreferrer');
        } catch (error) {
          return { success: false, message: `Unable to open YouTube: ${error instanceof Error ? error.message : 'browser launch failed'}.` };
        }

        return {
          success: true,
          message: `Opened YouTube search for "${query}".`,
          targetUrl: youtubeUrl,
        };
      }

      case 'OPEN_APP': {
        const app = (action.appName || '').toLowerCase();
        const urls: Record<string, string> = {
          youtube: 'https://www.youtube.com', chrome: 'https://www.google.com', 'google chrome': 'https://www.google.com',
          browser: 'https://www.google.com', maps: 'https://maps.google.com', 'google maps': 'https://maps.google.com',
          whatsapp: 'https://web.whatsapp.com', spotify: 'https://open.spotify.com',
          instagram: 'https://www.instagram.com', netflix: 'https://www.netflix.com',
        };
        const targetUrl = urls[app] || '';

        if (!targetUrl) return { success: false, message: `${action.appName} can only be launched from the Android app.` };

        if (targetUrl) {
          try {
            window.open(targetUrl, '_blank', 'noopener,noreferrer');
          } catch (error) {
            return { success: false, message: `Unable to open ${action.appName}: ${error instanceof Error ? error.message : 'browser launch failed'}.` };
          }
        }

        return {
          success: true,
          message: `Opened ${action.appName}.`,
          targetUrl: targetUrl || undefined,
        };
      }

      case 'OPEN_SETTINGS': {
        return {
          success: false,
          message: 'Opening device settings is available in the Android app.',
        };
      }

      default:
        return { success: false, message: 'Unsupported device action.' };
    }
  }
}
