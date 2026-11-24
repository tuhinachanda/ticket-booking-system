import React, { useEffect, useState } from 'react';
import { onSlowRequestChange } from '../services/api';
import { Loader2, Server, X } from 'lucide-react';

export const ServerWakeupBanner: React.FC = () => {
  const [isWakingUp, setIsWakingUp] = useState(false);
  const [dismissed, setDismissed] = useState(false);

  useEffect(() => {
    const unsubscribe = onSlowRequestChange((isSlow) => {
      setIsWakingUp(isSlow);
      if (!isSlow) {
        setDismissed(false);
      }
    });
    return unsubscribe;
  }, []);

  if (!isWakingUp || dismissed) return null;

  return (
    <div className="server-wakeup-banner" role="status" aria-live="polite">
      <div className="server-wakeup-content">
        <div className="server-wakeup-icon">
          <Server size={18} />
          <Loader2 size={16} className="spinning wakeup-spin" />
        </div>
        <div className="server-wakeup-body">
          <span className="server-wakeup-title">Connecting to Backend Server...</span>
          <span className="server-wakeup-desc">
            Free-tier backends (Render) automatically sleep after inactivity and take 15–35 seconds to spin up. Hang tight, your request is in progress!
          </span>
        </div>
        <button
          className="server-wakeup-close"
          onClick={() => setDismissed(true)}
          title="Dismiss notice"
          aria-label="Dismiss notice"
        >
          <X size={16} />
        </button>
      </div>
    </div>
  );
};
