import { describe, it, expect, beforeAll } from "vitest";
import {
  base32Encode,
  base32Decode,
  rawP1363ToDer,
  derToRawP1363,
  cleanBase64,
  base64ToBytes,
  bytesToBase64,
  signLicense,
  verifyLicense,
  RFC4648_ALPHABET,
} from "../src/crypto.js";

describe("Cryptographic Core Unit Tests", () => {
  let samplePublicKeyB64: string;
  let samplePrivateKeyB64: string;
  let alternatePublicKeyB64: string;
  let alternatePrivateKeyB64: string;

  beforeAll(async () => {
    // Generate primary keypair
    const kp1 = (await crypto.subtle.generateKey(
      { name: "ECDSA", namedCurve: "P-256" },
      true,
      ["sign", "verify"]
    )) as CryptoKeyPair;
    const spki1 = (await crypto.subtle.exportKey("spki", kp1.publicKey)) as ArrayBuffer;
    const pkcs81 = (await crypto.subtle.exportKey("pkcs8", kp1.privateKey)) as ArrayBuffer;
    samplePublicKeyB64 = bytesToBase64(new Uint8Array(spki1));
    samplePrivateKeyB64 = bytesToBase64(new Uint8Array(pkcs81));

    // Generate alternate keypair for negative verification tests
    const kp2 = (await crypto.subtle.generateKey(
      { name: "ECDSA", namedCurve: "P-256" },
      true,
      ["sign", "verify"]
    )) as CryptoKeyPair;
    const spki2 = (await crypto.subtle.exportKey("spki", kp2.publicKey)) as ArrayBuffer;
    const pkcs82 = (await crypto.subtle.exportKey("pkcs8", kp2.privateKey)) as ArrayBuffer;
    alternatePublicKeyB64 = bytesToBase64(new Uint8Array(spki2));
    alternatePrivateKeyB64 = bytesToBase64(new Uint8Array(pkcs82));
  });

  // =========================================================================
  // 1. Base32 Tests
  // =========================================================================
  describe("Base32 Encoding & Decoding (RFC 4648)", () => {
    const rfcVectors: [string, string][] = [
      ["", ""],
      ["f", "MY"],
      ["fo", "MZXQ"],
      ["foo", "MZXW6"],
      ["foob", "MZXW6YQ"],
      ["fooba", "MZXW6YTB"],
      ["foobar", "MZXW6YTBOI"],
    ];

    it("matches all RFC 4648 standard test vectors", () => {
      for (const [plain, expected] of rfcVectors) {
        const encoded = base32Encode(new TextEncoder().encode(plain));
        expect(encoded).toBe(expected);

        const decodedBytes = base32Decode(expected);
        const decodedPlain = new TextDecoder().decode(decodedBytes);
        expect(decodedPlain).toBe(plain);
      }
    });

    it("round-trips arbitrary binary buffers of variable lengths (0 to 64 bytes)", () => {
      for (let len = 0; len <= 64; len++) {
        const buffer = new Uint8Array(len);
        for (let j = 0; j < len; j++) buffer[j] = (j * 43 + 17) & 0xff;

        const encoded = base32Encode(buffer);
        const decoded = base32Decode(encoded);
        expect(decoded).toEqual(buffer);
      }
    });

    it("decodes lowercase input into identical bytes", () => {
      const decoded = base32Decode("mzxw6ytboi");
      expect(new TextDecoder().decode(decoded)).toBe("foobar");
    });

    it("ignores trailing '=' padding gracefully", () => {
      const decoded = base32Decode("MZXW6YTBOI======");
      expect(new TextDecoder().decode(decoded)).toBe("foobar");
    });

    it("throws on invalid characters outside the alphabet", () => {
      expect(() => base32Decode("MZXW8")).toThrow("Invalid Base32 character: 8");
      expect(() => base32Decode("MZXW9")).toThrow("Invalid Base32 character: 9");
      expect(() => base32Decode("MZXW!")).toThrow("Invalid Base32 character: !");
      expect(() => base32Decode("MZXW-")).toThrow("Invalid Base32 character: -");
    });

    it("throws on incomplete quantum or non-zero trailing bits", () => {
      // 1 char = 5 bits (cannot form an 8-bit byte)
      expect(() => base32Decode("M")).toThrow("incomplete quantum");
      // 3 chars = 15 bits (1 byte + 7 remainder bits >= 5)
      expect(() => base32Decode("MZX")).toThrow("incomplete quantum");
      // 6 chars = 30 bits (3 bytes + 6 remainder bits >= 5)
      expect(() => base32Decode("MZXW6Y")).toThrow("incomplete quantum");
      // Non-zero trailing bit: 'Z' in place of 'Y'
      expect(() => base32Decode("MZ")).toThrow("incomplete quantum or non-zero trailing bits");
    });
  });

  // =========================================================================
  // 2. IEEE P1363 to DER Conversion Tests
  // =========================================================================
  describe("IEEE P1363 to ASN.1 DER Conversion (rawP1363ToDer)", () => {
    it("throws if input is not exactly 64 bytes", () => {
      expect(() => rawP1363ToDer(new Uint8Array(63))).toThrow("expected 64");
      expect(() => rawP1363ToDer(new Uint8Array(65))).toThrow("expected 64");
      expect(() => rawP1363ToDer(new Uint8Array(0))).toThrow("expected 64");
    });

    it("converts signature with MSB=0 without prepending 0x00", () => {
      const raw = new Uint8Array(64);
      raw[0] = 0x7f; // r MSB=0
      raw[32] = 0x40; // s MSB=0

      const der = rawP1363ToDer(raw);
      expect(der[0]).toBe(0x30); // SEQUENCE tag
      expect(der[1]).toBe(68); // 2 + 32 + 2 + 32 = 68
      expect(der[2]).toBe(0x02); // INTEGER tag for r
      expect(der[3]).toBe(32); // r length = 32
      expect(der[4]).toBe(0x7f);

      expect(der[36]).toBe(0x02); // INTEGER tag for s
      expect(der[37]).toBe(32); // s length = 32
      expect(der[38]).toBe(0x40);
      expect(der.length).toBe(70);
    });

    it("converts signature with MSB=1 by prepending 0x00", () => {
      const raw = new Uint8Array(64);
      raw[0] = 0x80; // r MSB=1
      raw[32] = 0xff; // s MSB=1

      const der = rawP1363ToDer(raw);
      expect(der[0]).toBe(0x30);
      expect(der[1]).toBe(70); // 2 + 33 + 2 + 33 = 70
      expect(der[2]).toBe(0x02);
      expect(der[3]).toBe(33); // r length = 33
      expect(der[4]).toBe(0x00); // prepended zero
      expect(der[5]).toBe(0x80);

      expect(der[37]).toBe(0x02);
      expect(der[38]).toBe(33); // s length = 33
      expect(der[39]).toBe(0x00); // prepended zero
      expect(der[40]).toBe(0xff);
      expect(der.length).toBe(72);
    });

    it("strips leading zeros correctly while respecting MSB rule", () => {
      const raw = new Uint8Array(64);
      // r has leading zero, next byte has MSB=0
      raw[0] = 0x00;
      raw[1] = 0x35;
      // s has leading zero, next byte has MSB=1
      raw[32] = 0x00;
      raw[33] = 0x95;

      const der = rawP1363ToDer(raw);
      // r stripped to 31 bytes
      expect(der[2]).toBe(0x02);
      expect(der[3]).toBe(31);
      expect(der[4]).toBe(0x35);

      // s stripped to 31 bytes, then 0x00 prepended -> 32 bytes
      const sOffset = 2 + 2 + 31;
      expect(der[sOffset]).toBe(0x02);
      expect(der[sOffset + 1]).toBe(32);
      expect(der[sOffset + 2]).toBe(0x00);
      expect(der[sOffset + 3]).toBe(0x95);
    });
  });

  // =========================================================================
  // 3. Bidirectional DER <-> P1363 Round-Trip Tests
  // =========================================================================
  describe("Bidirectional Conversion (rawP1363ToDer <-> derToRawP1363)", () => {
    it("round-trips 20 live Web Crypto signatures with exact byte equality", async () => {
      const kp = (await crypto.subtle.generateKey(
        { name: "ECDSA", namedCurve: "P-256" },
        true,
        ["sign", "verify"]
      )) as CryptoKeyPair;

      for (let i = 0; i < 20; i++) {
        const payload = new TextEncoder().encode(`TEST_MESSAGE_${i}_${Date.now()}`);
        const rawSig = new Uint8Array(
          await crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, kp.privateKey, payload)
        );

        const derSig = rawP1363ToDer(rawSig);
        const restoredRaw = derToRawP1363(derSig);

        expect(restoredRaw).toEqual(rawSig);

        // Verify restored signature passes Web Crypto verification
        const valid = await crypto.subtle.verify(
          { name: "ECDSA", hash: "SHA-256" },
          kp.publicKey,
          restoredRaw,
          payload
        );
        expect(valid).toBe(true);
      }
    });
  });

  // =========================================================================
  // 4. Base64 & Key Import/Export Utilities
  // =========================================================================
  describe("Base64 & Key Cleaning", () => {
    it("cleans PEM headers, whitespace, and surrounding quotes", () => {
      const pem = `-----BEGIN PRIVATE KEY-----\nMIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQg\n-----END PRIVATE KEY-----`;
      expect(cleanBase64(pem)).toBe("MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQg");

      const quoted = `"MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQg"`;
      expect(cleanBase64(quoted)).toBe("MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQg");
    });

    it("round-trips base64ToBytes and bytesToBase64 without Node Buffer", () => {
      const bytes = new Uint8Array([10, 20, 30, 128, 255, 0]);
      const b64 = bytesToBase64(bytes);
      const restored = base64ToBytes(b64);
      expect(restored).toEqual(bytes);
    });
  });

  // =========================================================================
  // 5. License Signing & Minting (signLicense)
  // =========================================================================
  describe("Token Generation (signLicense)", () => {
    it("generates a token conforming to HRY1.<PAYLOAD_B32>.<SIG_B32>", async () => {
      const deviceId = "HRY-A8F2-91C0-4E7B";
      const issuedAt = 1700000000;
      const token = await signLicense(deviceId, samplePrivateKeyB64, issuedAt, "PRO");

      expect(token.startsWith("HRY1.")).toBe(true);
      const parts = token.split(".");
      expect(parts.length).toBe(3);

      // Verify payload
      const payloadBytes = base32Decode(parts[1]);
      const payloadStr = new TextDecoder().decode(payloadBytes);
      expect(payloadStr).toBe("HRY-A8F2-91C0-4E7B|PRO|1700000000");

      // Verify signature is DER
      const sigDer = base32Decode(parts[2]);
      expect(sigDer[0]).toBe(0x30); // DER SEQUENCE
    });

    it("supports PEM formatted private keys with headers", async () => {
      const lines = samplePrivateKeyB64.match(/.{1,64}/g)?.join("\n");
      const pemKey = `-----BEGIN PRIVATE KEY-----\n${lines}\n-----END PRIVATE KEY-----`;

      const token = await signLicense("HRY-1111-2222-3333", pemKey, 1700000000);
      const result = await verifyLicense(token, samplePublicKeyB64);
      expect(result.valid).toBe(true);
      expect(result.deviceId).toBe("HRY-1111-2222-3333");
    });
  });

  // =========================================================================
  // 6. License Verification & Adversarial Tamper Rejection
  // =========================================================================
  describe("License Verification & Tamper Resistance (verifyLicense)", () => {
    it("verifies a valid token successfully", async () => {
      const deviceId = "HRY-TEST-8888-9999";
      const issuedAt = 1730000000;
      const token = await signLicense(deviceId, samplePrivateKeyB64, issuedAt, "PRO");

      const res = await verifyLicense(token, samplePublicKeyB64);
      expect(res.valid).toBe(true);
      expect(res.deviceId).toBe(deviceId);
      expect(res.tier).toBe("PRO");
      expect(res.issuedAt).toBe(issuedAt);
    });

    it("rejects token when device ID is tampered in payload", async () => {
      const token = await signLicense("HRY-REAL-1111-2222", samplePrivateKeyB64, 1700000000);
      const parts = token.split(".");

      // Tamper payload to unauthorized device ID
      const tamperedPayload = base32Encode(
        new TextEncoder().encode("HRY-FAKE-1111-2222|PRO|1700000000")
      );
      const tamperedToken = `HRY1.${tamperedPayload}.${parts[2]}`;

      const res = await verifyLicense(tamperedToken, samplePublicKeyB64);
      expect(res.valid).toBe(false);
      expect(res.error).toBe("Signature verification failed");
    });

    it("rejects token when tier is modified", async () => {
      const token = await signLicense("HRY-REAL-1111-2222", samplePrivateKeyB64, 1700000000, "FREE");
      const parts = token.split(".");

      const tamperedPayload = base32Encode(
        new TextEncoder().encode("HRY-REAL-1111-2222|PRO|1700000000")
      );
      const tamperedToken = `HRY1.${tamperedPayload}.${parts[2]}`;

      const res = await verifyLicense(tamperedToken, samplePublicKeyB64);
      expect(res.valid).toBe(false);
      expect(res.error).toBe("Signature verification failed");
    });

    it("rejects token when a signature byte is flipped", async () => {
      const token = await signLicense("HRY-REAL-1111-2222", samplePrivateKeyB64, 1700000000);
      const parts = token.split(".");

      const sigBytes = base32Decode(parts[2]);
      sigBytes[sigBytes.length - 1] ^= 0x55; // flip bits
      const tamperedSig = base32Encode(sigBytes);
      const tamperedToken = `HRY1.${parts[1]}.${tamperedSig}`;

      const res = await verifyLicense(tamperedToken, samplePublicKeyB64);
      expect(res.valid).toBe(false);
      expect(res.error).toBe("Signature verification failed");
    });

    it("rejects token signed with an unauthorized private key", async () => {
      const token = await signLicense("HRY-REAL-1111-2222", alternatePrivateKeyB64, 1700000000);
      const res = await verifyLicense(token, samplePublicKeyB64);
      expect(res.valid).toBe(false);
      expect(res.error).toBe("Signature verification failed");
    });

    it("rejects malformed token strings and wrong prefix versions", async () => {
      expect((await verifyLicense("", samplePublicKeyB64)).valid).toBe(false);
      expect((await verifyLicense("MALFORMED", samplePublicKeyB64)).valid).toBe(false);
      expect((await verifyLicense("HRY1.ONLY_TWO_PARTS", samplePublicKeyB64)).valid).toBe(false);
      expect((await verifyLicense("HRY2.PAYLOAD.SIG", samplePublicKeyB64)).valid).toBe(false);
      expect((await verifyLicense("HRY1.PAYLOAD!.SIG", samplePublicKeyB64)).valid).toBe(false);
    });

    it("rejects malformed payload structure", async () => {
      const malformedPayloadB32 = base32Encode(new TextEncoder().encode("HRY-ONLY-TWO-SEGS|PRO"));
      const sigB32 = base32Encode(new Uint8Array(64));
      const res = await verifyLicense(`HRY1.${malformedPayloadB32}.${sigB32}`, samplePublicKeyB64);
      expect(res.valid).toBe(false);
      expect(res.error).toContain("Malformed payload format");
    });
  });
});
