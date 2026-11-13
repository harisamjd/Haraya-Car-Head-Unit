import { Env } from '../types.js';
import { getSupabaseClient, getDatabaseBackup } from '../db/supabase.js';
import { sendTelegramDocument, sendTelegramMessage } from '../services/telegram.js';

export async function handleWeeklyBackup(env: Env): Promise<{ success: boolean; totalOrders: number; totalLicenses: number }> {
  if (!env.TELEGRAM_ADMIN_CHAT_ID || !env.TELEGRAM_BOT_TOKEN) {
    console.warn('[CronBackup] Telegram credentials not configured. Skipping automated backup dispatch.');
    return { success: false, totalOrders: 0, totalLicenses: 0 };
  }

  try {
    const supabase = getSupabaseClient(env);
    const backup = await getDatabaseBackup(supabase);
    const jsonContent = JSON.stringify(backup, null, 2);
    const dateStr = new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
    const filename = `haraya_weekly_backup_${dateStr}.json`;

    await sendTelegramDocument(
      env,
      env.TELEGRAM_ADMIN_CHAT_ID,
      filename,
      jsonContent,
      `📦 *Haraya Dock Weekly Database Backup*\nDate: ${new Date().toUTCString()}\nOrders: ${backup.total_orders}\nLicenses: ${backup.total_licenses}`
    );

    return {
      success: true,
      totalOrders: backup.total_orders,
      totalLicenses: backup.total_licenses
    };
  } catch (err: any) {
    console.error('[CronBackup] Failed weekly database backup:', err);
    await sendTelegramMessage(
      env,
      env.TELEGRAM_ADMIN_CHAT_ID,
      `🚨 *Weekly Database Backup Failed!*\nError: ${err.message}`
    );
    return { success: false, totalOrders: 0, totalLicenses: 0 };
  }
}

