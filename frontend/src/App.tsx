import React, { useState } from 'react';
import { AuthProvider, useAuth } from './context/AuthContext';
import { Navbar } from './components/Navbar';
import { AuthModal } from './components/AuthModal';
import { EventDiscovery } from './components/EventDiscovery';
import { SeatGrid } from './components/SeatGrid';
import { PaymentModal } from './components/PaymentModal';
import { MyBookings } from './components/MyBookings';
import { AdminDashboard } from './components/AdminDashboard';
import { ToastContainer } from './components/Toast';
import type { ToastMessage } from './components/Toast';
import type { BookingResponse, Event, Show } from './types';

const MainApp: React.FC = () => {
  const { isAdmin } = useAuth();
  const [activeTab, setActiveTab] = useState<'events' | 'my-bookings' | 'admin'>('events');
  const [selectedShow, setSelectedShow] = useState<{ show: Show; event: Event } | null>(null);
  const [activeBooking, setActiveBooking] = useState<BookingResponse | null>(null);
  const [toasts, setToasts] = useState<ToastMessage[]>([]);

  const showToast = (message: string, type: 'error' | 'success' | 'info' = 'info') => {
    const id = Date.now().toString() + Math.random().toString();
    setToasts((prev) => [...prev, { id, message, type }]);
    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, 4500);
  };

  const handleDismissToast = (id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  };

  return (
    <div className="app-layout">
      <Navbar activeTab={activeTab} setActiveTab={(tab) => {
        setSelectedShow(null);
        setActiveTab(tab);
      }} />

      <main className="main-content">
        {activeTab === 'events' && (
          <>
            {!selectedShow ? (
              <EventDiscovery
                onSelectShow={(show, event) => setSelectedShow({ show, event })}
              />
            ) : (
              <SeatGrid
                show={selectedShow.show}
                event={selectedShow.event}
                onBack={() => setSelectedShow(null)}
                onBookingCreated={(booking) => setActiveBooking(booking)}
                onShowToast={showToast}
              />
            )}
          </>
        )}

        {activeTab === 'my-bookings' && <MyBookings onShowToast={showToast} />}

        {activeTab === 'admin' && isAdmin && <AdminDashboard onShowToast={showToast} />}
      </main>

      {/* Global Modals & Notifications */}
      <AuthModal />

      {activeBooking && (
        <PaymentModal
          booking={activeBooking}
          onClose={() => setActiveBooking(null)}
          onPaymentComplete={() => {
            setActiveBooking(null);
            setSelectedShow(null);
            setActiveTab('my-bookings');
          }}
          onShowToast={showToast}
        />
      )}

      <ToastContainer toasts={toasts} onDismiss={handleDismissToast} />
    </div>
  );
};

export const App: React.FC = () => {
  return (
    <AuthProvider>
      <MainApp />
    </AuthProvider>
  );
};

export default App;
