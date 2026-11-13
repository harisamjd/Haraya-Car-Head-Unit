import { Hono } from 'hono';
import { Env, WebhookPaymentRequest, WebhookPaymentResponse } from '../types.js';
import { getSupabaseClient, findOrderById, markOrderPaid, createOrUpdateLicense } from '../db/supabase.js';
import { signLicense } from '../crypto.js';
import { sendLicenseEmail } from '../services/email.js';
import { sendTelegramMessage } from '../services/telegram.js';
import { getPaymentAdapter } from '../services/payment.js';

export const webhookRouter = new Hono<{ Bindings: Env }>();

webhookRouter.post('/payment', async (c) => {
  const payload = await c.req.json<WebhookPaymentRequest>().catch(() => null);
  const paymentAdapter = getPaymentAdapter(c.env);
  const verification = await paymentAdapter.verifyWebhook(payload);

  if (!verification.valid) {
    return c.json({ error: 'INVALID_WEBHOOK', message: 'Invalid webhook payload or signature' }, 400);
  }

  const supabase = getSupabaseClient(c.env);
  const order = await findOrderById(supabase, verification.orderId);

  if (!order) {
    return c.json({ error: 'ORDER_NOT_FOUND', message: `Order ${verification.orderId} not found` }, 404);
  }

  // Idempotency check: Already processed?
  if (order.status === 'PAID' && order.license_key) {
    const response: WebhookPaymentResponse = {
      success: true,
      order_id: order.order_id,
      license_key: order.license_key,
      message: 'Order already paid and license issued.'
    };
    return c.json(response, 200);
  }

  if (verification.status === 'PAID') {
    // 1. Mint cryptographic license key
    const licenseKey = await signLicense(order.device_id, c.env.LICENSE_PRIVATE_KEY);

    // 2. Update Order
    await markOrderPaid(supabase, order.order_id, licenseKey);

    // 3. Upsert into Licenses table
    const customerEmail = payload?.customer_email || order.email || undefined;
    const customerPhone = payload?.customer_phone || order.phone || undefined;
    await createOrUpdateLicense(supabase, {
      device_id: order.device_id,
      license_key: licenseKey,
      customer_email: customerEmail || null,
      customer_phone: customerPhone || null,
      order_id: order.order_id,
      status: 'ACTIVE'
    });

    // 4. Asynchronous Notifications (failure-tolerant)
    const emailPromise = customerEmail
      ? sendLicenseEmail(c.env, {
          to: customerEmail,
          deviceId: order.device_id,
          licenseKey,
          orderId: order.order_id
        })
      : Promise.resolve();

    const telegramPromise = c.env.TELEGRAM_ADMIN_CHAT_ID
      ? sendTelegramMessage(
          c.env,
          c.env.TELEGRAM_ADMIN_CHAT_ID,
          `💰 *New Payment Confirmed!*\n\n*Order:* \`${order.order_id}\`\n*Device:* \`${order.device_id}\`\n*Amount:* ${order.amount} ${order.currency}\n*License Key:*\n\`${licenseKey}\``
        )
      : Promise.resolve();

    try {
      c.executionCtx.waitUntil(Promise.allSettled([emailPromise, telegramPromise]));
    } catch {
      await Promise.allSettled([emailPromise, telegramPromise]);
    }

    const response: WebhookPaymentResponse = {
      success: true,
      order_id: order.order_id,
      license_key: licenseKey
    };
    return c.json(response, 200);
  }

  // If status is failed or expired
  return c.json({ success: true, order_id: order.order_id, message: 'Status updated' }, 200);
});
