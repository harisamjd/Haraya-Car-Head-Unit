import { Hono } from 'hono';
import { Env, TelegramUpdate } from '../types.js';
import { getSupabaseClient, findLicenseByDeviceId, createOrUpdateLicense, getDatabaseBackup } from '../db/supabase.js';
import { signLicense } from '../crypto.js';
import { sendTelegramMessage, sendTelegramDocument } from '../services/telegram.js';
import { sendLicenseEmail } from '../services/email.js';

export const telegramRouter = new Hono<{ Bindings: Env }>();

telegramRouter.post('/webhook', async (c) => {
  // Secret header verification
  const secretHeader = c.req.header('X-Telegram-Bot-Api-Secret-Token');
  if (c.env.TELEGRAM_SECRET_TOKEN && secretHeader !== c.env.TELEGRAM_SECRET_TOKEN) {
    return c.json({ error: 'UNAUTHORIZED' }, 401);
  }

  const update = await c.req.json<TelegramUpdate>().catch(() => null);
  const msg = update?.message;
  if (!msg || typeof msg.text !== 'string') {
    return c.json({ ok: true }, 200);
  }

  const chatId = msg.chat.id;
  const text = msg.text.trim();

  // Admin authorization check
  const adminChatId = c.env.TELEGRAM_ADMIN_CHAT_ID;
  if (adminChatId && String(chatId) !== String(adminChatId)) {
    await sendTelegramMessage(c.env, chatId, '⛔ *Access Denied*: You are not authorized to access Haraya Dock Admin Bot.');
    return c.json({ ok: true }, 200);
  }

  const supabase = getSupabaseClient(c.env);

  // Command: /gen <device_id> [email]
  if (text.startsWith('/gen')) {
    const parts = text.split(/\s+/);
    const deviceId = parts[1];
    const customerEmail = parts[2];

    if (!deviceId) {
      await sendTelegramMessage(c.env, chatId, '⚠️ *Usage:* `/gen <device_id> [customer_email]`\nExample: `/gen HRY-A8F2-91C0-4E7B user@example.com`');
      return c.json({ ok: true }, 200);
    }

    try {
      const licenseKey = await signLicense(deviceId, c.env.LICENSE_PRIVATE_KEY);
      await createOrUpdateLicense(supabase, {
        device_id: deviceId,
        license_key: licenseKey,
        customer_email: customerEmail || null,
        customer_phone: null,
        order_id: `MANUAL_${Date.now()}`,
        status: 'ACTIVE'
      });

      let reply = `✅ *License Generated Successfully!*\n\n*Device ID:* \`${deviceId}\`\n\n*License Key (tap to copy):*\n\`${licenseKey}\``;
      if (customerEmail) {
        reply += `\n\n*Customer Email:* ${customerEmail}`;
        await sendLicenseEmail(c.env, { to: customerEmail, deviceId, licenseKey });
      }

      await sendTelegramMessage(c.env, chatId, reply);
    } catch (err: any) {
      await sendTelegramMessage(c.env, chatId, `❌ *Error generating license:* ${err.message}`);
    }
    return c.json({ ok: true }, 200);
  }

  // Command: /check <device_id>
  if (text.startsWith('/check')) {
    const parts = text.split(/\s+/);
    const deviceId = parts[1];

    if (!deviceId) {
      await sendTelegramMessage(c.env, chatId, '⚠️ *Usage:* `/check <device_id>`');
      return c.json({ ok: true }, 200);
    }

    const license = await findLicenseByDeviceId(supabase, deviceId);
    if (!license) {
      await sendTelegramMessage(c.env, chatId, `🔍 *No license found* for device ID \`${deviceId}\`.`);
    } else {
      const reply = `📄 *License Found*\n\n*Device ID:* \`${license.device_id}\`\n*Status:* ${license.status}\n*Email:* ${license.customer_email || 'N/A'}\n*Order ID:* ${license.order_id || 'N/A'}\n*Issued At:* ${license.created_at}\n\n*Key:*\n\`${license.license_key}\``;
      await sendTelegramMessage(c.env, chatId, reply);
    }
    return c.json({ ok: true }, 200);
  }

  // Command: /backup
  if (text.startsWith('/backup')) {
    await sendTelegramMessage(c.env, chatId, '⏳ Generating database backup...');
    try {
      const backup = await getDatabaseBackup(supabase);
      const jsonContent = JSON.stringify(backup, null, 2);
      const dateStr = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
      const filename = `haraya_backup_${dateStr}.json`;

      await sendTelegramDocument(
        c.env,
        chatId,
        filename,
        jsonContent,
        `📦 *Haraya Dock Backup*\nTotal Orders: ${backup.total_orders}\nTotal Licenses: ${backup.total_licenses}`
      );
    } catch (err: any) {
      await sendTelegramMessage(c.env, chatId, `❌ *Backup failed:* ${err.message}`);
    }
    return c.json({ ok: true }, 200);
  }

  // Command: /help or default
  const helpText = `🤖 *Haraya Dock Admin Bot Help*\n\n` +
    `• \`/gen <device_id> [email]\` — Generate and register a manual Pro license.\n` +
    `• \`/check <device_id>\` — Check license registration status.\n` +
    `• \`/backup\` — Export and download complete JSON database backup.\n` +
    `• \`/help\` — Show this help message.`;
  await sendTelegramMessage(c.env, chatId, helpText);
  return c.json({ ok: true }, 200);
});
