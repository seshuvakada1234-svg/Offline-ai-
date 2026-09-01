import React from 'react';
import { AssistantAction } from '../types';
import { AlertTriangle, ShieldAlert, X } from 'lucide-react';

interface ActionConfirmationDialogProps {
  action: AssistantAction | null;
  isOpen: boolean;
  onConfirm: (action: AssistantAction) => void;
  onCancel: () => void;
}

export const ActionConfirmationDialog: React.FC<ActionConfirmationDialogProps> = ({
  action,
  isOpen,
  onConfirm,
  onCancel,
}) => {
  if (!isOpen || !action) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-md animate-in fade-in duration-100" id="action-confirm-dialog">
      <div className="bg-[#0e0e12]/95 backdrop-blur-2xl border border-amber-500/30 rounded-3xl max-w-md w-full p-5 shadow-2xl shadow-black/90 space-y-4">
        <div className="flex items-start justify-between">
          <div className="flex items-center gap-3">
            <div className="p-2.5 rounded-2xl bg-amber-500/10 border border-amber-500/30 text-amber-400">
              <ShieldAlert className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-base font-bold text-white">Action Confirmation Required</h3>
              <p className="text-xs text-zinc-400">This action requires explicit user permission before execution.</p>
            </div>
          </div>
          <button
            type="button"
            onClick={onCancel}
            className="p-2 rounded-xl text-zinc-400 hover:text-white hover:bg-white/10 border border-transparent hover:border-white/10 transition-colors cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="p-3.5 bg-white/[0.02] rounded-2xl border border-white/10 space-y-1.5 text-xs backdrop-blur-md">
          <div className="text-zinc-400 font-medium">Requested Action:</div>
          <div className="font-semibold text-amber-300 text-sm">{action.type}</div>
          {action.phoneNumber && (
            <div className="text-zinc-300">
              Recipient: <span className="font-mono text-white">{action.phoneNumber}</span>
            </div>
          )}
          {action.messageText && (
            <div className="text-zinc-300">
              Message: <span className="italic text-zinc-100">"{action.messageText}"</span>
            </div>
          )}
        </div>

        <div className="flex items-center justify-end gap-2.5 pt-2">
          <button
            type="button"
            onClick={onCancel}
            className="px-4 py-2 rounded-xl bg-white/5 hover:bg-white/10 text-zinc-300 font-medium text-xs border border-white/10 transition-colors cursor-pointer"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={() => onConfirm(action)}
            className="px-4 py-2 rounded-xl bg-amber-600 hover:bg-amber-500 text-white font-bold text-xs transition-all shadow-lg shadow-amber-500/30 cursor-pointer active:scale-95"
          >
            Confirm & Execute
          </button>
        </div>
      </div>
    </div>
  );
};
