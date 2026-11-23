import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { X, User as UserIcon, Shield, Lock, Mail } from 'lucide-react';

export const AuthModal: React.FC = () => {
  const { isAuthModalOpen, closeAuthModal, login, register } = useAuth();
  const [isLoginMode, setIsLoginMode] = useState(true);

  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  if (!isAuthModalOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      if (isLoginMode) {
        await login(email, password);
      } else {
        await register(name, email, password);
      }
    } catch (err: any) {
      const msg = err.response?.data?.message || err.response?.data?.error || 'Authentication failed. Please check your credentials.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  const handleQuickFill = (demoEmail: string, demoPass: string) => {
    setEmail(demoEmail);
    setPassword(demoPass);
    setIsLoginMode(true);
    setError(null);
  };

  return (
    <div className="modal-overlay">
      <div className="modal-content auth-modal">
        <button className="modal-close-btn" onClick={closeAuthModal} aria-label="Close modal">
          <X size={20} />
        </button>

        <div className="auth-header">
          <h2 className="modal-title">{isLoginMode ? 'Welcome Back' : 'Create an Account'}</h2>
          <p className="modal-subtitle">
            {isLoginMode
              ? 'Sign in to access your reserved tickets and manage bookings'
              : 'Register to start reserving seats and enjoying live events'}
          </p>
        </div>

        {/* Quick-fill demo account buttons */}
        <div className="demo-accounts-box">
          <span className="demo-label">Quick Demo Access:</span>
          <div className="demo-btn-group">
            <button
              type="button"
              className="demo-btn user-demo-btn"
              onClick={() => handleQuickFill('user@ticket.com', 'User@123')}
            >
              <UserIcon size={14} />
              <span>Customer Demo</span>
            </button>
            <button
              type="button"
              className="demo-btn admin-demo-btn"
              onClick={() => handleQuickFill('admin@ticket.com', 'Admin@123')}
            >
              <Shield size={14} />
              <span>Admin Demo</span>
            </button>
          </div>
        </div>

        {error && <div className="auth-error-banner">{error}</div>}

        <form onSubmit={handleSubmit} className="auth-form">
          {!isLoginMode && (
            <div className="form-group">
              <label>Full Name</label>
              <div className="input-wrapper">
                <UserIcon size={18} className="input-icon" />
                <input
                  type="text"
                  required
                  placeholder="John Doe"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                />
              </div>
            </div>
          )}

          <div className="form-group">
            <label>Email Address</label>
            <div className="input-wrapper">
              <Mail size={18} className="input-icon" />
              <input
                type="email"
                required
                placeholder="you@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </div>
          </div>

          <div className="form-group">
            <label>Password</label>
            <div className="input-wrapper">
              <Lock size={18} className="input-icon" />
              <input
                type="password"
                required
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </div>
          </div>

          <button type="submit" className="primary-btn submit-auth-btn" disabled={loading}>
            {loading ? 'Please wait...' : isLoginMode ? 'Sign In' : 'Create Account'}
          </button>
        </form>

        <div className="auth-toggle">
          {isLoginMode ? (
            <p>
              Don't have an account?{' '}
              <button
                type="button"
                className="link-btn"
                onClick={() => {
                  setIsLoginMode(false);
                  setError(null);
                }}
              >
                Register here
              </button>
            </p>
          ) : (
            <p>
              Already have an account?{' '}
              <button
                type="button"
                className="link-btn"
                onClick={() => {
                  setIsLoginMode(true);
                  setError(null);
                }}
              >
                Sign in
              </button>
            </p>
          )}
        </div>
      </div>
    </div>
  );
};
