import { Env } from '../types.js';

export async function sendTelegramMessage(
  env: Env,
  chatId: string | number,
  text: string,
  parseMode: 'Markdown' | 'HTML' = 'Markdown'
): Promise<{ success: boolean; error?: string }> {
  if (!env.TELEGRAM_BOT_TOKEN) {
    console.warn('[TelegramService] TELEGRAM_BOT_TOKEN not configured. Skipping message.');
    return { success: false, error: 'NO_BOT_TOKEN' };
  }

  try {
    const res = await fetch(`https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/sendMessage`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        chat_id: chatId,
        text,
        parse_mode: parseMode
      })
    });

    if (!res.ok) {
      const errText = await res.text();
      console.error(`[TelegramService] sendMessage error (${res.status}): ${errText}`);
      return { success: false, error: errText };
    }
    return { success: true };
  } catch (err: any) {
    console.error('[TelegramService] Network error in sendMessage:', err);
    return { success: false, error: err.message };
  }
}

export async function sendTelegramDocument(
  env: Env,
  chatId: string | number,
  filename: string,
  content: string | Uint8Array,
  caption?: string
): Promise<{ success: boolean; error?: string }> {
  if (!env.TELEGRAM_BOT_TOKEN) {
    console.warn('[TelegramService] TELEGRAM_BOT_TOKEN not configured. Skipping document.');
    return { success: false, error: 'NO_BOT_TOKEN' };
  }

  try {
    const formData = new FormData();
    formData.append('chat_id', String(chatId));

    const blob = typeof content === 'string'
      ? new Blob([content], { type: 'application/json' })
      : new Blob([content]);

    formData.append('document', blob, filename);
    if (caption) {
      formData.append('caption', caption);
      formData.append('parse_mode', 'Markdown');
    }

    const res = await fetch(`https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/sendDocument`, {
      method: 'POST',
      body: formData
    });

    if (!res.ok) {
      const errText = await res.text();
      console.error(`[TelegramService] sendDocument error (${res.status}): ${errText}`);
      return { success: false, error: errText };
    }
    return { success: true };
  } catch (err: any) {
    console.error('[TelegramService] Network error in sendDocument:', err);
    return { success: false, error: err.message };
  }
}

