package org.example.core;

public class AOF {

	public static void dumpAllAOF() throws java.io.IOException {
		java.nio.file.Path path = java.nio.file.Paths.get(org.example.config.Configuration.AOFFile);
		try (java.io.BufferedWriter writer = java.nio.file.Files.newBufferedWriter(path,
				java.nio.charset.StandardCharsets.UTF_8,
				java.nio.file.StandardOpenOption.CREATE,
				java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
				java.nio.file.StandardOpenOption.WRITE)) {
			for (java.util.Map.Entry<String, org.example.model.ObjectStore> entry : Store.snapshot().entrySet()) {
				String key = entry.getKey();
				org.example.model.ObjectStore obj = entry.getValue();
				if (obj == null) {
					continue;
				}
				if (obj.isExpired()) {
					continue;
				}
				dumpKey(key, obj, writer);
			}
		}
	}

	
// TODO: Support non-kv data structures
// TODO: Support sync write
	public static void dumpKey(String key, org.example.model.ObjectStore obj, java.io.BufferedWriter writer) throws java.io.IOException {
		Object value = obj.getValue();
		String v = value == null ? "" : String.valueOf(value);
		long expiresAt = obj.getExpiresAt();
		long now = java.time.Instant.now().toEpochMilli();
		StringBuilder line = new StringBuilder();
		line.append("SET ").append(key).append(' ').append(v);
		if (expiresAt != -1) {
			long remainingMs = expiresAt - now;
			if (remainingMs > 0) {
				long remainingSec = (remainingMs + 999) / 1000; // round up to next second
				line.append(' ').append("EX ").append(remainingSec);
			}
		}
		writer.write(line.toString());
		writer.newLine();
	}
}
