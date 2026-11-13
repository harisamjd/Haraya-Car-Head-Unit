import { Env } from '../types.js';

export interface SendLicenseEmailOptions {
  to: string;
  deviceId: string;
  licenseKey: string;
  orderId?: string;
}

export async function sendLicenseEmail(
  env: Env,
  options: SendLicenseEmailOptions
): Promise<{ success: boolean; error?: string }> {
  if (!env.RESEND_API_KEY) {
    console.warn('[EmailService] RESEND_API_KEY not configured. Skipping email dispatch.');
    return { success: false, error: 'NO_API_KEY' };
  }

  const fromAddress = env.RESEND_FROM_EMAIL || 'Haraya Dock <onboarding@resend.dev>';
  const whatsappUrl = `https://wa.me/6285195963456?text=Hi%20Haraya%20Dock%20Support%2C%20I%20need%20help%20with%20my%20license%20for%20Device%20ID%3A%20${encodeURIComponent(options.deviceId)}`;

  const html = `
    <!DOCTYPE html>
    <html>
    <head><meta charset="utf-8"></head>
    <body style="background:#0F1115; color:#F0F2F5; font-family:-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; margin:0; padding:32px 16px;">
      <div style="max-width:560px; margin:0 auto; background:#181B22; border-radius:12px; border:1px solid #2B303C; padding:32px;">
        <div style="display:flex; align-items:center; margin-bottom:24px;">
          <h1 style="color:#00E5FF; font-size:24px; margin:0; font-weight:700;">Haraya Dock Pro</h1>
        </div>
        <p style="font-size:16px; line-height:1.5; color:#D0D5DD;">Thank you for your purchase! Your Haraya Dock Pro license has been issued.</p>
        
        <div style="background:#0B0D11; border-radius:8px; border:1px solid #323846; padding:16px; margin:24px 0;">
          <div style="font-size:12px; text-transform:uppercase; color:#8C95A6; letter-spacing:0.05em; margin-bottom:6px;">Device ID</div>
          <div style="font-size:15px; font-family:monospace; color:#FFFFFF; margin-bottom:16px;">${options.deviceId}</div>
          
          <div style="font-size:12px; text-transform:uppercase; color:#8C95A6; letter-spacing:0.05em; margin-bottom:6px;">License Key</div>
          <div style="background:#131720; padding:12px; border-radius:6px; font-size:14px; font-family:monospace; color:#00E5FF; word-break:break-all; border:1px dashed #00E5FF55;">
            ${options.licenseKey}
          </div>
        </div>

        <h3 style="color:#FFFFFF; font-size:16px; margin-top:24px; margin-bottom:8px;">How to Activate</h3>
        <ol style="color:#B4BCCB; font-size:14px; line-height:1.6; padding-left:20px; margin:0 0 24px 0;">
          <li><strong>Auto-Activation:</strong> If your vehicle head unit is connected to Wi-Fi / hotspot, Haraya Dock will automatically detect this license within a few seconds.</li>
          <li><strong>Offline Activation:</strong> If your head unit is offline, tap <em>Settings &gt; Pro License</em> in Haraya Dock, and paste or type this license key manually.</li>
        </ol>

        <div style="border-top:1px solid #2B303C; padding-top:20px; font-size:13px; color:#8C95A6;">
          Need help? Contact support on WhatsApp: 
          <a href="${whatsappUrl}" style="color:#00E5FF; text-decoration:none;">+62 851-9596-3456</a>
        </div>
      </div>
    </body>
    </html>
  `;

  try {
    const res = await fetch('https://api.resend.com/emails', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${env.RESEND_API_KEY}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        from: fromAddress,
        to: [options.to],
        subject: '🎉 Your Haraya Dock Pro License Key',
        html
      })
    });

    if (!res.ok) {
      const errText = await res.text();
      console.error(`[EmailService] Resend API error (${res.status}): ${errText}`);
      return { success: false, error: errText };
    }
    return { success: true };
  } catch (err: any) {
    console.error('[EmailService] Network error sending email:', err);
    return { success: false, error: err.message };
  }
}

