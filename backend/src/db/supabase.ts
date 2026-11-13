import { createClient, SupabaseClient } from '@supabase/supabase-js';
import { Env, OrderRecord, LicenseRecord, DatabaseBackup } from '../types.js';

export function getSupabaseClient(env: Env): SupabaseClient {
  return createClient(env.SUPABASE_URL, env.SUPABASE_SERVICE_ROLE_KEY, {
    auth: {
      persistSession: false,
      autoRefreshToken: false,
      detectSessionInUrl: false
    }
  });
}

export async function findLicenseByDeviceId(
  supabase: SupabaseClient,
  deviceId: string
): Promise<LicenseRecord | null> {
  const { data, error } = await supabase
    .from('licenses')
    .select('*')
    .eq('device_id', deviceId)
    .single();

  if (error) {
    if (error.code === 'PGRST116') return null; // No rows found
    throw new Error(`Database error querying license: ${error.message}`);
  }
  return data as LicenseRecord;
}

export async function findOrderByDeviceId(
  supabase: SupabaseClient,
  deviceId: string
): Promise<OrderRecord | null> {
  const { data, error } = await supabase
    .from('orders')
    .select('*')
    .eq('device_id', deviceId)
    .order('created_at', { ascending: false })
    .limit(1)
    .maybeSingle();

  if (error) {
    throw new Error(`Database error querying order by device: ${error.message}`);
  }
  return data as OrderRecord | null;
}

export async function findOrderById(
  supabase: SupabaseClient,
  orderId: string
): Promise<OrderRecord | null> {
  const { data, error } = await supabase
    .from('orders')
    .select('*')
    .eq('order_id', orderId)
    .single();

  if (error) {
    if (error.code === 'PGRST116') return null;
    throw new Error(`Database error querying order by id: ${error.message}`);
  }
  return data as OrderRecord;
}

export async function createOrder(
  supabase: SupabaseClient,
  order: Omit<OrderRecord, 'created_at' | 'paid_at' | 'license_key'>
): Promise<OrderRecord> {
  const { data, error } = await supabase
    .from('orders')
    .insert([
      {
        order_id: order.order_id,
        device_id: order.device_id,
        email: order.email,
        phone: order.phone,
        amount: order.amount,
        currency: order.currency,
        status: order.status,
        payment_url: order.payment_url
      }
    ])
    .select()
    .single();

  if (error) {
    throw new Error(`Database error creating order: ${error.message}`);
  }
  return data as OrderRecord;
}

export async function markOrderPaid(
  supabase: SupabaseClient,
  orderId: string,
  licenseKey: string
): Promise<OrderRecord> {
  const paidAt = new Date().toISOString();
  const { data, error } = await supabase
    .from('orders')
    .update({
      status: 'PAID',
      license_key: licenseKey,
      paid_at: paidAt
    })
    .eq('order_id', orderId)
    .select()
    .single();

  if (error) {
    throw new Error(`Database error updating order to paid: ${error.message}`);
  }
  return data as OrderRecord;
}

export async function createOrUpdateLicense(
  supabase: SupabaseClient,
  license: Omit<LicenseRecord, 'created_at' | 'updated_at'>
): Promise<LicenseRecord> {
  const { data, error } = await supabase
    .from('licenses')
    .upsert(
      {
        device_id: license.device_id,
        license_key: license.license_key,
        customer_email: license.customer_email,
        customer_phone: license.customer_phone,
        order_id: license.order_id,
        status: license.status
      },
      { onConflict: 'device_id' }
    )
    .select()
    .single();

  if (error) {
    throw new Error(`Database error saving license: ${error.message}`);
  }
  return data as LicenseRecord;
}

export async function getDatabaseBackup(supabase: SupabaseClient): Promise<DatabaseBackup> {
  const [ordersRes, licensesRes] = await Promise.all([
    supabase.from('orders').select('*').order('created_at', { ascending: false }),
    supabase.from('licenses').select('*').order('created_at', { ascending: false })
  ]);

  if (ordersRes.error) throw new Error(`Orders backup error: ${ordersRes.error.message}`);
  if (licensesRes.error) throw new Error(`Licenses backup error: ${licensesRes.error.message}`);

  const orders = (ordersRes.data || []) as OrderRecord[];
  const licenses = (licensesRes.data || []) as LicenseRecord[];

  return {
    timestamp: new Date().toISOString(),
    version: '1.0',
    total_orders: orders.length,
    total_licenses: licenses.length,
    orders,
    licenses
  };
}

