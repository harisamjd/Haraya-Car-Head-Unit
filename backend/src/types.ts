export interface Env {
  ENVIRONMENT?: string;
  BASE_URL?: string;
  SUPABASE_URL: string;
  SUPABASE_SERVICE_ROLE_KEY: string;
  LICENSE_PRIVATE_KEY: string;
  RESEND_API_KEY?: string;
  RESEND_FROM_EMAIL?: string;
  TELEGRAM_BOT_TOKEN?: string;
  TELEGRAM_ADMIN_CHAT_ID?: string;
  TELEGRAM_SECRET_TOKEN?: string;
  MOCK_PAYMENT_ENABLED?: string; // "true" or "false"
  ADMIN_API_KEY?: string;
}

export type OrderStatus = 'PENDING' | 'PAID' | 'EXPIRED' | 'FAILED';

export interface OrderRecord {
  order_id: string;
  device_id: string;
  email: string | null;
  phone: string | null;
  amount: number;
  currency: string;
  status: OrderStatus;
  payment_url: string | null;
  license_key: string | null;
  created_at: string;
  paid_at: string | null;
}

export type LicenseStatus = 'ACTIVE' | 'REVOKED';

export interface LicenseRecord {
  device_id: string;
  license_key: string;
  customer_email: string | null;
  customer_phone: string | null;
  order_id: string | null;
  status: LicenseStatus;
  created_at: string;
  updated_at: string;
}

export interface CheckoutInitRequest {
  device_id: string;
  app_version?: string;
  email?: string;
  phone?: string;
}

export type CheckoutInitResponse =
  | {
      status: 'success';
      order_id: string;
      checkout_url: string;
      amount: number;
      currency: string;
      device_id: string;
    }
  | {
      status: 'ALREADY_ACTIVE';
      device_id: string;
      license_key: string;
      message: string;
    };

export interface CheckoutStatusPendingResponse {
  order_id: string;
  status: 'PENDING' | 'EXPIRED' | 'FAILED';
  device_id: string;
}

export interface CheckoutStatusPaidResponse {
  order_id: string;
  status: 'PAID';
  device_id: string;
  license_key: string;
  paid_at: string;
}

export type CheckoutStatusResponse = CheckoutStatusPendingResponse | CheckoutStatusPaidResponse;

export interface WebhookPaymentRequest {
  mode?: 'mock' | 'live';
  order_id: string;
  status: 'PAID' | 'FAILED' | 'EXPIRED';
  customer_email?: string;
  customer_phone?: string;
  payment_reference?: string;
}

export interface WebhookPaymentResponse {
  success: boolean;
  order_id: string;
  license_key?: string;
  message?: string;
}

export interface TelegramUpdate {
  update_id: number;
  message?: {
    message_id: number;
    from?: {
      id: number;
      is_bot: boolean;
      first_name: string;
      username?: string;
    };
    chat: {
      id: number;
      type: string;
      first_name?: string;
      username?: string;
    };
    date: number;
    text?: string;
  };
}

export interface DatabaseBackup {
  timestamp: string;
  version: string;
  total_orders: number;
  total_licenses: number;
  orders: OrderRecord[];
  licenses: LicenseRecord[];
}

