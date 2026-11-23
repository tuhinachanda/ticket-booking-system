export type Role = 'USER' | 'ADMIN';

export type ShowStatus = 'SCHEDULED' | 'CANCELLED' | 'COMPLETED';

export type SeatStatus = 'AVAILABLE' | 'HELD' | 'BOOKED' | 'SELECTED';

export type BookingStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'EXPIRED';

export type PaymentStatus = 'PENDING' | 'SUCCEEDED' | 'FAILED' | 'REFUNDED';

export interface User {
  id: number;
  name: string;
  email: string;
  role: Role;
  token: string;
}

export interface AuthResponse {
  token: string;
  userId: number;
  name: string;
  email: string;
  role: Role;
}

export interface Venue {
  id: number;
  name: string;
  city: string;
  address: string;
  totalRows: number;
  seatsPerRow: number;
  totalCapacity: number;
}

export interface Event {
  id: number;
  title: string;
  description?: string;
  category: string;
  durationMinutes: number;
  posterUrl?: string;
  createdAt: string;
}

export interface Show {
  id: number;
  eventId: number;
  eventTitle: string;
  venue: Venue;
  startTime: string;
  basePrice: number;
  status: ShowStatus;
  availableSeatsCount: number;
  totalSeatsCount: number;
}

export interface Seat {
  id: number;
  seatRow: string;
  seatNumber: number;
  price: number;
  effectiveStatus: SeatStatus;
}

export interface BookingSeat {
  showSeatId: number;
  seatRow: string;
  seatNumber: number;
  price: number;
}

export interface BookingResponse {
  id: number;
  userId: number;
  showId: number;
  showTitle: string;
  venueName: string;
  showStartTime: string;
  idempotencyKey: string;
  totalAmount: number;
  status: BookingStatus;
  holdExpiresAt?: string;
  createdAt: string;
  seats: BookingSeat[];
}

export interface UserBookingHistory {
  bookingId: number;
  eventTitle: string;
  venueName: string;
  showStartTime: string;
  seatLabels: string[];
  totalAmount: number;
  bookingStatus: BookingStatus;
  paymentStatus?: PaymentStatus;
  createdAt: string;
}

export interface PaymentResponse {
  paymentId: number;
  bookingId: number;
  amount: number;
  paymentStatus: PaymentStatus;
  bookingStatus: BookingStatus;
  transactionRef: string;
  createdAt: string;
}

export interface ShowOccupancyStat {
  showId: number;
  eventTitle: string;
  venueName: string;
  startTime: string;
  totalSeats: number;
  bookedSeats: number;
  occupancyPercentage: number;
  revenue: number;
}

export interface AdminStats {
  totalBookings: number;
  confirmedBookings: number;
  cancelledBookings: number;
  totalConfirmedRevenue: number;
  showStats: ShowOccupancyStat[];
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}
