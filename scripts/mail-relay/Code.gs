/**
 * Voyago 訂單確認信寄信服務（Google Apps Script 網頁應用程式）。
 *
 * 後端（Render 免費方案不能用 SMTP）用 HTTPS POST 呼叫這裡，由站長自己的 Gmail 寄出。
 * 只接受「訂單欄位」，信件內容在這裡組成：就算網址外流，也不能被拿來寄任意內容。
 *
 * 設定：專案設定 → 指令碼屬性，新增 RELAY_SECRET＝Render 上 MAIL_RELAY_SECRET 的值。
 */
const BRAND = 'Voyago 歐洲自助遊';
const SITE_URL = 'https://voyago-backend-c8fd.onrender.com';

function doPost(e) {
  try {
    const data = JSON.parse((e && e.postData && e.postData.contents) || '{}');
    const secret = PropertiesService.getScriptProperties().getProperty('RELAY_SECRET');
    if (!secret || data.secret !== secret) return reply({ ok: false, error: 'unauthorized' });
    if (data.type !== 'booking-confirmation') return reply({ ok: false, error: 'unsupported type' });

    const to = String(data.to || '').trim();
    if (!/^[^\s@<>"]+@[^\s@<>"]+\.[^\s@<>"]+$/.test(to)) return reply({ ok: false, error: 'bad recipient' });
    if (MailApp.getRemainingDailyQuota() < 1) return reply({ ok: false, error: 'daily quota exhausted' });

    const f = {
      name: clip(data.name, 50) || '旅客',
      bookingId: Number(data.bookingId) || 0,
      routeName: clip(data.routeName, 120),
      travelDate: clip(data.travelDate, 10).replace(/-/g, '/'),
      people: Number(data.people) || 0,
      totalPrice: Number(data.totalPrice) || 0,
      couponCode: clip(data.couponCode, 30),
      discountAmount: Number(data.discountAmount) || 0,
    };

    MailApp.sendEmail({
      to: to,
      subject: '【' + BRAND + '】訂單確認 #' + f.bookingId,
      name: BRAND,
      body: textBody(f),
      htmlBody: htmlBody(f),
    });
    return reply({ ok: true });
  } catch (err) {
    return reply({ ok: false, error: String(err && err.message || err) });
  }
}

/** 部署後用瀏覽器打開網址會看到這行，確認有部署成功（不會寄信）。 */
function doGet() {
  return reply({ ok: true, service: 'voyago-mail-relay' });
}

function reply(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}

function clip(v, n) {
  return String(v == null ? '' : v).replace(/[\r\n]+/g, ' ').trim().slice(0, n);
}

function money(n) {
  return 'NT$ ' + Number(n).toLocaleString('en-US');
}

function esc(s) {
  return String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

function textBody(f) {
  const lines = [
    f.name + ' 您好，', '', '感謝您的訂購，以下是您的訂單明細：', '',
    '行程：' + f.routeName,
    '出發日期：' + f.travelDate,
    '人數：' + f.people + ' 人',
  ];
  if (f.discountAmount > 0) lines.push('優惠碼 ' + f.couponCode + ' 折抵：' + money(f.discountAmount));
  lines.push('實付金額：' + money(f.totalPrice), '訂單編號：#' + f.bookingId, '',
    '查看訂單：' + SITE_URL + '/dashboard', '', '祝旅途愉快！', BRAND);
  return lines.join('\n');
}

function htmlBody(f) {
  const row = (k, v) => '<tr><td style="padding:6px 16px 6px 0;color:#64748b">' + k +
    '</td><td style="padding:6px 0;font-weight:600;color:#0f172a">' + v + '</td></tr>';
  return '<div style="font-family:-apple-system,\'Noto Sans TC\',sans-serif;max-width:520px;margin:auto;color:#0f172a">' +
    '<div style="padding:20px 0;border-bottom:3px solid #059669"><span style="font-size:24px;font-weight:800">Voyago</span>' +
    '<span style="font-size:13px;color:#64748b;margin-left:8px">歐洲自助遊</span></div>' +
    '<p style="margin:24px 0 8px">' + esc(f.name) + ' 您好，</p>' +
    '<p style="margin:0 0 16px">感謝您的訂購，付款已完成。以下是您的訂單明細：</p>' +
    '<table style="border-collapse:collapse;font-size:15px">' +
    row('行程', esc(f.routeName)) + row('出發日期', esc(f.travelDate)) + row('人數', f.people + ' 人') +
    (f.discountAmount > 0 ? row('優惠碼 ' + esc(f.couponCode), '− ' + money(f.discountAmount)) : '') +
    row('實付金額', '<span style="color:#059669">' + money(f.totalPrice) + '</span>') +
    row('訂單編號', '#' + f.bookingId) + '</table>' +
    '<p style="margin:28px 0"><a href="' + SITE_URL + '/dashboard" style="background:#059669;color:#fff;' +
    'padding:10px 20px;border-radius:999px;text-decoration:none;font-weight:600">查看我的訂單</a></p>' +
    '<p style="color:#64748b;font-size:13px">祝旅途愉快！<br>' + BRAND + '</p></div>';
}
