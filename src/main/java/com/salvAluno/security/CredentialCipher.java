package com.salvAluno.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;


// Cifra a senha do portal em repouso. A chave fica fora do banco, em arquivo local.
@Component
public class CredentialCipher {

	// Tamanho da chave de criptografia 
	private static final int KEY_BYTES = 32;
	private static final int IV_BYTES = 12;
	private static final int TAG_BITS = 128;

	// Chave de criptografia (armazenada em arquivo local) 
	private final SecretKey key;
	// Gerador de números aletórios
	private final SecureRandom random = new SecureRandom();

	// Construtor que carrega a chave de criptografia do arquivo local
	public CredentialCipher(@Value("${app.crypto.key-path:data/crypto.key}") String keyPath) throws IOException {
		this.key = new SecretKeySpec(loadOrCreateKey(Path.of(keyPath)), "AES");
	}

	public String encrypt(String plain) {
		try {
			byte[] iv = new byte[IV_BYTES];
			random.nextBytes(iv);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
			byte[] encrypted = cipher.doFinal(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			byte[] packed = new byte[iv.length + encrypted.length];
			System.arraycopy(iv, 0, packed, 0, iv.length);
			System.arraycopy(encrypted, 0, packed, iv.length, encrypted.length);
			return Base64.getEncoder().encodeToString(packed);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Falha ao criptografar a senha.", e);
		}
	}

	public String decrypt(String packedBase64) throws GeneralSecurityException {
		byte[] packed = Base64.getDecoder().decode(packedBase64);
		if (packed.length <= IV_BYTES) {
			throw new GeneralSecurityException("Credencial cifrada inválida.");
		}
		byte[] iv = new byte[IV_BYTES];
		byte[] encrypted = new byte[packed.length - IV_BYTES];
		System.arraycopy(packed, 0, iv, 0, IV_BYTES);
		System.arraycopy(packed, IV_BYTES, encrypted, 0, encrypted.length);
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
		byte[] plain = cipher.doFinal(encrypted);
		return new String(plain, java.nio.charset.StandardCharsets.UTF_8);
	}

	private static byte[] loadOrCreateKey(Path path) throws IOException {
		if (Files.exists(path)) {
			byte[] existing = Files.readAllBytes(path);
			if (existing.length != KEY_BYTES) {
				throw new IllegalStateException("A chave em " + path + " está inválida.");
			}
			return existing;
		}
		Files.createDirectories(path.getParent() == null ? Path.of(".") : path.getParent());
		byte[] created = new byte[KEY_BYTES];
		new SecureRandom().nextBytes(created);
		Files.write(path, created);
		try {
			Files.setPosixFilePermissions(path, java.util.Set.of(
					java.nio.file.attribute.PosixFilePermission.OWNER_READ,
					java.nio.file.attribute.PosixFilePermission.OWNER_WRITE
			));
		} catch (UnsupportedOperationException ignored) {
			// Windows não aplica permissão POSIX; o arquivo continua só na pasta local data/.
		}
		System.out.println("[SalvAluno] Chave de criptografia criada em " + path.toAbsolutePath());
		return created;
	}
}
