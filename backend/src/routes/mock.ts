import { Hono } from 'hono';
import { Env } from '../types.js';

export const mockRouter = new Hono<{ Bindings: Env }>();

mockRouter.get('/checkout', (c) => {
  const orderId = c.req.query('order_id') || 'ORD-DEMO-12345';
  const deviceId = c.req.query('device_id') || 'HRY-DEMO-1234-5678';
  const amount = c.req.query('amount') || '150000';

  const html = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Haraya Dock Mock Payment Simulator</title>
  <style>
    :root {
      --bg: #0D1117;
      --card-bg: #161B22;
      --border: #30363D;
      --text: #C9D1D9;
      --text-bright: #F0F6FC;
      --accent: #00E5FF;
      --accent-hover: #33EBFF;
      --success: #238636;
      --danger: #DA3633;
    }
    body {
      background: var(--bg);
      color: var(--text);
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      margin: 0;
      padding: 40px 16px;
      display: flex;
      justify-content: center;
    }
    .container {
      max-width: 460px;
      width: 100%;
      background: var(--card-bg);
      border: 1px solid var(--border);
      border-radius: 12px;
      padding: 28px;
      box-shadow: 0 8px 24px rgba(0,0,0,0.5);
    }
    h2 { margin-top: 0; color: var(--accent); }
    .badge {
      display: inline-block;
      padding: 4px 8px;
      background: rgba(0,229,255,0.15);
      color: var(--accent);
      border-radius: 6px;
      font-size: 12px;
      margin-bottom: 20px;
      font-weight: 600;
    }
    .info-row {
      display: flex;
      justify-content: space-between;
      margin-bottom: 12px;
      padding-bottom: 8px;
      border-bottom: 1px solid rgba(255,255,255,0.06);
      font-size: 14px;
    }
    .info-label { color: #8B949E; }
    .info-val { font-family: monospace; color: var(--text-bright); font-weight: 600; }
    .btn {
      width: 100%;
      padding: 14px;
      margin-top: 14px;
      border: none;
      border-radius: 8px;
      font-size: 15px;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.2s;
    }
    .btn-pay { background: var(--success); color: #fff; }
    .btn-pay:hover { background: #2EA043; }
    .btn-fail { background: #21262D; color: #F85149; border: 1px solid #30363D; }
    .btn-fail:hover { background: rgba(218,54,51,0.2); }
    #resultBox {
      display: none;
      margin-top: 20px;
      padding: 16px;
      border-radius: 8px;
      font-size: 14px;
      background: #0B0E14;
      border: 1px solid var(--accent);
    }
    .license-key {
      font-family: monospace;
      color: var(--accent);
      word-break: break-all;
      background: #111620;
      padding: 8px;
      border-radius: 4px;
      margin-top: 8px;
    }
  </style>
</head>
<body>
  <div class="container">
    <h2>Haraya Dock Pro</h2>
    <div class="badge">SANDBOX SIMULATION</div>
    <p>Complete payment simulation to unlock lifetime Pro features.</p>
    
    <div class="info-row">
      <span class="info-label">Order ID</span>
      <span class="info-val">${orderId}</span>
    </div>
    <div class="info-row">
      <span class="info-label">Device ID</span>
      <span class="info-val">${deviceId}</span>
    </div>
    <div class="info-row">
      <span class="info-label">Amount</span>
      <span class="info-val">IDR ${Number(amount).toLocaleString()} ($10 USD)</span>
    </div>

    <button id="payBtn" class="btn btn-pay" onclick="simulatePayment('PAID')">
      Simulate Successful Payment
    </button>
    <button id="failBtn" class="btn btn-fail" onclick="simulatePayment('FAILED')">
      Simulate Payment Failure
    </button>

    <div id="resultBox">
      <div id="statusTitle" style="font-weight:bold; margin-bottom:6px;"></div>
      <div id="statusMsg"></div>
      <div id="keyBox" style="display:none;">
        <div style="font-size:12px; color:#8B949E; margin-top:8px;">Issued License Key:</div>
        <div id="licenseKey" class="license-key"></div>
      </div>
    </div>
  </div>

  <script>
    async function simulatePayment(status) {
      const payBtn = document.getElementById('payBtn');
      const failBtn = document.getElementById('failBtn');
      const resultBox = document.getElementById('resultBox');
      const statusTitle = document.getElementById('statusTitle');
      const statusMsg = document.getElementById('statusMsg');
      const keyBox = document.getElementById('keyBox');
      const licenseKey = document.getElementById('licenseKey');

      payBtn.disabled = true;
      failBtn.disabled = true;
      resultBox.style.display = 'block';
      statusTitle.innerText = 'Processing...';
      statusMsg.innerText = 'Communicating with webhook...';

      try {
        const res = await fetch('/api/webhook/payment', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            order_id: '${orderId}',
            status: status,
            mode: 'mock'
          })
        });
        const data = await res.json();
        if (res.ok && data.success) {
          if (status === 'PAID') {
            statusTitle.style.color = '#3FB950';
            statusTitle.innerText = 'Payment Successful!';
            statusMsg.innerText = 'Your license is now active on this device.';
            if (data.license_key) {
              keyBox.style.display = 'block';
              licenseKey.innerText = data.license_key;
            }
          } else {
            statusTitle.style.color = '#F85149';
            statusTitle.innerText = 'Payment Failed';
            statusMsg.innerText = 'Order was marked as failed.';
          }
        } else {
          statusTitle.style.color = '#F85149';
          statusTitle.innerText = 'Simulation Error';
          statusMsg.innerText = data.message || 'Unknown error occurred.';
        }
      } catch (err) {
        statusTitle.style.color = '#F85149';
        statusTitle.innerText = 'Network Error';
        statusMsg.innerText = err.message;
      }
    }
  </script>
</body>
</html>`;

  return c.html(html);
});

