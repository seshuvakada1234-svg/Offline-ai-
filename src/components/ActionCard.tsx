import React from 'react';
import { AssistantAction } from '../types';
import { ActionHandler } from '../services/actionHandler';
import {
  Youtube,
  ExternalLink,
  Settings,
  Smartphone,
  Phone,
  MessageSquare,
  CheckCircle2,
  AlertTriangle,
  Play,
} from 'lucide-react';

interface ActionCardProps {
  action: AssistantAction;
  onActionUpdated?: (action: AssistantAction) => void;
}

export const ActionCard: React.FC<ActionCardProps> = ({ action, onActionUpdated }) => {
  const [isExecuting, setIsExecuting] = React.useState(false);

  const handleExecute = async () => {
    setIsExecuting(true);
    const result = await ActionHandler.executeAction(action);
    setIsExecuting(false);
    if (onActionUpdated) {
      onActionUpdated({ ...action, executed: result.success, resultMessage: result.message });
    }
  };

  const getActionIcon = () => {
    switch (action.type) {
      case 'SEARCH_YOUTUBE':
        return <Youtube className="w-5 h-5 text-red-500" />;
      case 'OPEN_APP':
        return <Smartphone className="w-5 h-5 text-emerald-400" />;
      case 'OPEN_SETTINGS':
        return <Settings className="w-5 h-5 text-zinc-300" />;
      case 'OPEN_URL':
        return <ExternalLink className="w-5 h-5 text-teal-400" />;
      case 'MAKE_CALL':
        return <Phone className="w-5 h-5 text-blue-400" />;
      case 'SEND_SMS':
        return <MessageSquare className="w-5 h-5 text-amber-400" />;
      default:
        return <Smartphone className="w-5 h-5 text-emerald-400" />;
    }
  };

  const getActionTitle = () => {
    switch (action.type) {
      case 'SEARCH_YOUTUBE':
        return `YouTube Search: "${action.query || 'Telugu songs'}"`;
      case 'OPEN_APP':
        return `Launch App: ${action.appName || 'Application'}`;
      case 'OPEN_SETTINGS':
        return 'Open System Settings';
      case 'OPEN_URL':
        return `Open Web Page: ${action.url}`;
      case 'MAKE_CALL':
        return `Call ${action.phoneNumber}`;
      case 'SEND_SMS':
        return `Send SMS to ${action.phoneNumber}`;
      default:
        return `Intent: ${action.type}`;
    }
  };

  return (
    <div
      id={`action-card-${action.id}`}
      className="mt-2 p-3.5 rounded-xl bg-white/[0.03] border border-white/10 shadow-sm backdrop-blur-md flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs"
    >
      <div className="flex items-start gap-2.5">
        <div className="p-2 rounded-lg bg-white/5 border border-white/10 flex-shrink-0 shadow-inner">
          {getActionIcon()}
        </div>

        <div className="space-y-0.5">
          <div className="flex items-center gap-1.5 flex-wrap">
            <span className="font-bold text-zinc-100 text-xs sm:text-sm">{getActionTitle()}</span>
            {action.executed ? (
              <span className="inline-flex items-center gap-1 text-[10px] text-emerald-400 bg-emerald-500/10 px-2 py-0.5 rounded-full border border-emerald-500/20 font-mono font-medium">
                <CheckCircle2 className="w-3 h-3" />
                Intent Dispatched
              </span>
            ) : action.requiresConfirmation ? (
              <span className="inline-flex items-center gap-1 text-[10px] text-amber-300 bg-amber-500/10 px-2 py-0.5 rounded-full border border-amber-500/20 font-mono font-medium">
                <AlertTriangle className="w-3 h-3" />
                Requires Confirmation
              </span>
            ) : null}
          </div>

          <div className="text-[11px] font-mono text-zinc-400 flex items-center gap-2">
            <span>Intent: <code className="text-indigo-400">{action.intentAction || 'android.intent.action.VIEW'}</code></span>
            {action.appName && <span>Package: <code className="text-zinc-300">{action.appName}</code></span>}
          </div>
        </div>
      </div>

      <div className="flex items-center gap-2 self-end sm:self-center flex-shrink-0">
        <button
          type="button"
          id={`execute-action-${action.id}`}
          onClick={handleExecute}
          disabled={isExecuting}
          className="px-3.5 py-1.5 rounded-xl bg-white/5 hover:bg-white/10 text-zinc-100 font-medium border border-white/10 flex items-center gap-1.5 transition-all shadow-sm active:scale-95 text-xs cursor-pointer"
        >
          <ExternalLink className="w-3.5 h-3.5 text-indigo-400" />
          {action.type === 'SEARCH_YOUTUBE' ? 'Open in YouTube' : 'Launch Intent'}
        </button>
      </div>
    </div>
  );
};
