import React from 'react';
import { useAuth } from '../context/AuthContext';
import { Ticket, Calendar, Shield, LogOut, User as UserIcon } from 'lucide-react';

interface NavbarProps {
  activeTab: 'events' | 'my-bookings' | 'admin';
  setActiveTab: (tab: 'events' | 'my-bookings' | 'admin') => void;
}

export const Navbar: React.FC<NavbarProps> = ({ activeTab, setActiveTab }) => {
  const { user, isAuthenticated, isAdmin, logout, openAuthModal } = useAuth();

  return (
    <header className="navbar">
      <div className="navbar-container">
        <div className="brand" onClick={() => setActiveTab('events')}>
          <div className="brand-icon">
            <Ticket size={24} />
          </div>
          <span className="brand-title">TicketPass</span>
        </div>

        <nav className="nav-links">
          <button
            className={`nav-tab ${activeTab === 'events' ? 'active' : ''}`}
            onClick={() => setActiveTab('events')}
          >
            <Calendar size={18} />
            <span>Discover Events</span>
          </button>

          {isAuthenticated && (
            <button
              className={`nav-tab ${activeTab === 'my-bookings' ? 'active' : ''}`}
              onClick={() => setActiveTab('my-bookings')}
            >
              <Ticket size={18} />
              <span>My Bookings</span>
            </button>
          )}

          {isAdmin && (
            <button
              className={`nav-tab admin-tab ${activeTab === 'admin' ? 'active' : ''}`}
              onClick={() => setActiveTab('admin')}
            >
              <Shield size={18} />
              <span>Admin Dashboard</span>
            </button>
          )}
        </nav>

        <div className="nav-auth">
          {isAuthenticated ? (
            <div className="user-profile">
              <div className="user-info">
                <span className="user-name">{user?.name}</span>
                <span className={`user-role ${user?.role === 'ADMIN' ? 'role-admin' : 'role-user'}`}>
                  {user?.role}
                </span>
              </div>
              <button className="logout-btn" onClick={logout} title="Sign Out">
                <LogOut size={18} />
              </button>
            </div>
          ) : (
            <button className="sign-in-btn" onClick={openAuthModal}>
              <UserIcon size={18} />
              <span>Sign In / Demo</span>
            </button>
          )}
        </div>
      </div>
    </header>
  );
};
