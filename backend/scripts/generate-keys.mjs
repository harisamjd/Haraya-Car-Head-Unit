#!/usr/bin/env node
/**
 * Haraya Dock - ECDSA P-256 Keypair Generator
 * Generates SPKI Public Key (Base64) for Android Client
 * and PKCS#8 Private Key (Base64) for Cloudflare Worker.
 */

async function generateKeyPair() {
  console.log("Generating ECDSA P-256 (secp256r1) keypair...");

  const keyPair = await crypto.subtle.generateKey(
    { name: "ECDSA", namedCurve: "P-256" },
    true,
    ["sign", "verify"]
  );

  const spkiBuffer = await crypto.subtle.exportKey("spki", keyPair.publicKey);
  const pkcs8Buffer = await crypto.subtle.exportKey("pkcs8", keyPair.privateKey);

  const publicKeyB64 = Buffer.from(spkiBuffer).toString("base64");
  const privateKeyB64 = Buffer.from(pkcs8Buffer).toString("base64");

  // Format PEM blocks
  const formatPem = (b64, type) => {
    const lines = b64.match(/.{1,64}/g).join("\n");
    return `-----BEGIN ${type}-----\n${lines}\n-----END ${type}-----`;
  };
  const publicPem = formatPem(publicKeyB64, "PUBLIC KEY");
  const privatePem = formatPem(privateKeyB64, "PRIVATE KEY");

  // Self-test round-trip verification
  const testData = new TextEncoder().encode("HARAYA_KEYGEN_SELF_TEST_PAYLOAD");
  const testSig = await crypto.subtle.sign(
    { name: "ECDSA", hash: "SHA-256" },
    keyPair.privateKey,
    testData
  );
  const isValid = await crypto.subtle.verify(
    { name: "ECDSA", hash: "SHA-256" },
    keyPair.publicKey,
    testSig,
    testData
  );

  if (!isValid) {
    console.error("FATAL: Keypair generation self-test failed!");
    process.exit(1);
  }

  console.log("\n================================================================================");
  console.log("  HARAYA DOCK - CRYPTOGRAPHIC KEYPAIR GENERATION SUCCESSFUL");
  console.log("================================================================================\n");

  console.log("1. ANDROID CLIENT PUBLIC KEY (X.509 SPKI Base64):");
  console.log("   File: app/src/main/java/com/haraya/dock/license/LicenseCrypto.kt\n");
  console.log(`   private const val PUBLIC_KEY_BASE64 =\n       "${publicKeyB64}"\n`);

  console.log("--------------------------------------------------------------------------------");
  console.log("2. BACKEND PRIVATE KEY (PKCS#8 Base64):");
  console.log("   Local Dev: Add to backend/.dev.vars:");
  console.log(`   LICENSE_PRIVATE_KEY=${privateKeyB64}\n`);
  console.log("   Production Cloudflare Secret:");
  console.log(`   npx wrangler secret put LICENSE_PRIVATE_KEY\n`);

  console.log("--------------------------------------------------------------------------------");
  console.log("3. PEM FORMATS (For archival / backup):");
  console.log("\n" + publicPem + "\n");
  console.log(privatePem + "\n");
  console.log("================================================================================\n");
}

generateKeyPair().catch((err) => {
  console.error("Error generating keys:", err);
  process.exit(1);
});

