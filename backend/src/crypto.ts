/**
 * Haraya Dock - Serverless Backend Cryptographic Core
 * - RFC 4648 Base32 Unpadded Encoding / Strict Decoding
 * - IEEE P1363 to ASN.1 DER Conversion (for Android java.security.Signature)
 * - ASN.1 DER to IEEE P1363 Conversion (for Web Crypto verifyLicense)
 * - ECDSA P-256 Token Minting & Verification
 */

export const RFC4648_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

export interface LicenseVerifyResult {
  valid: boolean;
  deviceId?: string;
  tier?: string;
  issuedAt?: number;
  error?: string;
}

/**
 * Encodes a byte buffer into an RFC 4648 Base32 uppercase string without padding.
 */
export function base32Encode(buffer: Uint8Array): string {
  let bits = 0;
  let value = 0;
  let output = "";

  for (let i = 0; i < buffer.length; i++) {
    value = (value << 8) | buffer[i];
    bits += 8;
    while (bits >= 5) {
      output += RFC4648_ALPHABET[(value >>> (bits - 5)) & 31];
      bits -= 5;
    }
  }

  if (bits > 0) {
    output += RFC4648_ALPHABET[(value << (5 - bits)) & 31];
  }

  return output;
}

/**
 * Decodes an RFC 4648 Base32 string into a Uint8Array.
 * Case-insensitive, strips trailing '=', and enforces strict quantum validity.
 */
export function base32Decode(str: string): Uint8Array {
  const clean = str.toUpperCase().replace(/=+$/, "");
  let bits = 0;
  let value = 0;
  const result: number[] = [];

  for (let i = 0; i < clean.length; i++) {
    const idx = RFC4648_ALPHABET.indexOf(clean[i]);
    if (idx === -1) {
      throw new Error(`Invalid Base32 character: ${clean[i]}`);
    }
    value = (value << 5) | idx;
    bits += 5;
    if (bits >= 8) {
      result.push((value >>> (bits - 8)) & 255);
      bits -= 8;
    }
  }

  // Strict validation: incomplete quantums or non-zero trailing bits are invalid
  if (bits >= 5 || (value & ((1 << bits) - 1)) !== 0) {
    throw new Error("Invalid Base32 encoding: incomplete quantum or non-zero trailing bits");
  }

  return new Uint8Array(result);
}

/**
 * Converts a 64-byte IEEE P1363 raw signature (r || s) into an ASN.1 DER SEQUENCE.
 * Compliant with ITU-T X.690 / RFC 3279 §2.2.3.
 */
export function rawP1363ToDer(rawSig: Uint8Array): Uint8Array {
  if (rawSig.length !== 64) {
    throw new Error(`Invalid IEEE P1363 signature length: expected 64, got ${rawSig.length}`);
  }

  let r = rawSig.subarray(0, 32);
  let s = rawSig.subarray(32, 64);

  // Strip leading zeros for r (keep at least 1 byte)
  let rStart = 0;
  while (rStart < 31 && r[rStart] === 0) rStart++;
  r = r.subarray(rStart);
  const rBytes = Array.from(r);
  if (r[0] & 0x80) rBytes.unshift(0x00); // Prepend 0x00 if sign bit is set

  // Strip leading zeros for s (keep at least 1 byte)
  let sStart = 0;
  while (sStart < 31 && s[sStart] === 0) sStart++;
  s = s.subarray(sStart);
  const sBytes = Array.from(s);
  if (s[0] & 0x80) sBytes.unshift(0x00); // Prepend 0x00 if sign bit is set

  const seqLen = 2 + rBytes.length + 2 + sBytes.length;
  return new Uint8Array([
    0x30,
    seqLen,
    0x02,
    rBytes.length,
    ...rBytes,
    0x02,
    sBytes.length,
    ...sBytes,
  ]);
}

/**
 * Converts an ASN.1 DER ECDSA signature back to a 64-byte IEEE P1363 raw signature.
 * Enables verification using Web Crypto API's crypto.subtle.verify.
 */
export function derToRawP1363(derSig: Uint8Array): Uint8Array {
  if (derSig.length < 8 || derSig[0] !== 0x30) {
    throw new Error("Invalid DER signature: missing SEQUENCE tag");
  }

  let offset = 2;
  if (derSig[1] & 0x80) {
    const lenBytes = derSig[1] & 0x7f;
    offset = 2 + lenBytes;
  }

  // Parse r
  if (derSig[offset] !== 0x02) {
    throw new Error("Invalid DER signature: missing INTEGER tag for r");
  }
  const rLen = derSig[offset + 1];
  offset += 2;
  let r = derSig.subarray(offset, offset + rLen);
  offset += rLen;

  // Parse s
  if (derSig[offset] !== 0x02) {
    throw new Error("Invalid DER signature: missing INTEGER tag for s");
  }
  const sLen = derSig[offset + 1];
  offset += 2;
  let s = derSig.subarray(offset, offset + sLen);

  // Normalize r to 32 bytes (strip leading zero if length > 32, pad if < 32)
  while (r.length > 32 && r[0] === 0) r = r.subarray(1);
  const raw = new Uint8Array(64);
  raw.set(r, 32 - r.length);

  // Normalize s to 32 bytes
  while (s.length > 32 && s[0] === 0) s = s.subarray(1);
  raw.set(s, 64 - s.length);

  return raw;
}

/**
 * Cleans a Base64 key string by removing PEM headers, surrounding quotes, and whitespace.
 */
export function cleanBase64(str: string): string {
  return str
    .replace(/^["']|["']$/g, "")
    .replace(/-----[^\n]+-----/g, "")
    .replace(/\s+/g, "");
}

/**
 * Decodes a Base64 string into a Uint8Array without requiring Node.js Buffer.
 */
export function base64ToBytes(b64: string): Uint8Array {
  const binaryStr = atob(cleanBase64(b64));
  const bytes = new Uint8Array(binaryStr.length);
  for (let i = 0; i < binaryStr.length; i++) {
    bytes[i] = binaryStr.charCodeAt(i);
  }
  return bytes;
}

/**
 * Encodes a Uint8Array into a Base64 string without requiring Node.js Buffer.
 */
export function bytesToBase64(bytes: Uint8Array): string {
  let binaryStr = "";
  for (let i = 0; i < bytes.byteLength; i++) {
    binaryStr += String.fromCharCode(bytes[i]);
  }
  return btoa(binaryStr);
}

/**
 * Signs a device license and produces a formatted token:
 * HRY1.<PAYLOAD_B32>.<SIG_B32>
 *
 * @param deviceId Hardware Device ID (e.g. HRY-A8F2-91C0-4E7B)
 * @param privateKeyB64 PKCS#8 private key in Base64 or PEM format
 * @param issuedAt Optional unix timestamp in seconds (defaults to current time)
 * @param tier License tier (defaults to "PRO")
 */
export async function signLicense(
  deviceId: string,
  privateKeyB64: string,
  issuedAt?: number,
  tier = "PRO"
): Promise<string> {
  const binaryKey = base64ToBytes(privateKeyB64);

  const privateKey = await crypto.subtle.importKey(
    "pkcs8",
    binaryKey,
    { name: "ECDSA", namedCurve: "P-256" },
    false,
    ["sign"]
  );

  const ts = issuedAt !== undefined ? issuedAt : Math.floor(Date.now() / 1000);
  const payload = `${deviceId}|${tier}|${ts}`;
  const data = new TextEncoder().encode(payload);

  const rawSig = await crypto.subtle.sign(
    { name: "ECDSA", hash: { name: "SHA-256" } },
    privateKey,
    data
  );

  const derSig = rawP1363ToDer(new Uint8Array(rawSig));
  const payloadB32 = base32Encode(data);
  const sigB32 = base32Encode(derSig);

  return `HRY1.${payloadB32}.${sigB32}`;
}

/**
 * Verifies a Haraya Dock license token offline.
 *
 * @param token Token formatted as HRY1.<PAYLOAD_B32>.<SIG_B32>
 * @param publicKeyB64 X.509 SPKI public key in Base64 or PEM format
 */
export async function verifyLicense(
  token: string,
  publicKeyB64: string
): Promise<LicenseVerifyResult> {
  if (!token || typeof token !== "string") {
    return { valid: false, error: "Invalid token input: string expected" };
  }

  const parts = token.trim().split(".");
  if (parts.length !== 3) {
    return { valid: false, error: "Invalid token structure: expected 3 parts" };
  }

  const [prefix, payloadB32, sigB32] = parts;
  if (prefix !== "HRY1") {
    return { valid: false, error: "Unsupported token version: expected HRY1" };
  }

  let payloadBytes: Uint8Array;
  let sigDerBytes: Uint8Array;

  try {
    payloadBytes = base32Decode(payloadB32);
  } catch (err: any) {
    return { valid: false, error: `Invalid Base32 payload: ${err.message}` };
  }

  try {
    sigDerBytes = base32Decode(sigB32);
  } catch (err: any) {
    return { valid: false, error: `Invalid Base32 signature: ${err.message}` };
  }

  const payloadStr = new TextDecoder().decode(payloadBytes);
  const segments = payloadStr.split("|");
  if (segments.length !== 3) {
    return { valid: false, error: "Malformed payload format: expected deviceId|tier|issuedAt" };
  }

  const [deviceId, tier, issuedAtStr] = segments;
  const issuedAt = parseInt(issuedAtStr, 10);
  if (isNaN(issuedAt)) {
    return { valid: false, error: "Invalid issuedAt timestamp" };
  }

  try {
    const rawSig = derToRawP1363(sigDerBytes);
    const binaryKey = base64ToBytes(publicKeyB64);

    const publicKey = await crypto.subtle.importKey(
      "spki",
      binaryKey,
      { name: "ECDSA", namedCurve: "P-256" },
      false,
      ["verify"]
    );

    const isValid = await crypto.subtle.verify(
      { name: "ECDSA", hash: { name: "SHA-256" } },
      publicKey,
      rawSig,
      payloadBytes
    );

    if (!isValid) {
      return { valid: false, error: "Signature verification failed" };
    }

    return { valid: true, deviceId, tier, issuedAt };
  } catch (err: any) {
    return { valid: false, error: err.message || "Cryptographic verification error" };
  }
}

