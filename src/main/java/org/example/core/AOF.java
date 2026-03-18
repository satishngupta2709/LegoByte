package org.example.core;

import java.util.logging.Logger;
public class AOF {

	private static final Logger logger= Logger.getLogger(AOF.class.getName());
	public static void dumpAllAOF() throws java.io.IOException {
		logger.info("Dumping all AOF files");
		java.nio.file.Path path = java.nio.file.Paths.get(org.example.config.Configuration.AOFFile);
		try (java.io.BufferedWriter writer = java.nio.file.Files.newBufferedWriter(path,
				java.nio.charset.StandardCharsets.UTF_8,
				java.nio.file.StandardOpenOption.CREATE,
				java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
				java.nio.file.StandardOpenOption.WRITE)) {
			for (java.util.Map.Entry<String, org.example.model.ObjectStore<?>> entry : Store.snapshot().entrySet()) {
				String key = entry.getKey();
				org.example.model.ObjectStore<?> obj = entry.getValue();
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
	public static void dumpKey(String key, org.example.model.ObjectStore<?> obj, java.io.BufferedWriter writer)
			throws java.io.IOException {
		Object value = obj.getValue();
		if (value == null)
			return;

		long expiresAt = obj.getExpiresAt();
		long now = java.time.Instant.now().toEpochMilli();
		long remainingSec = -1;
		if (expiresAt != -1) {
			long remainingMs = expiresAt - now;
			if (remainingMs > 0) {
				remainingSec = (remainingMs + 999) / 1000;
			} else {
				return; // Expired
			}
		}

		if (obj.getType() == org.example.model.ValueType.SET) {
			java.util.List<String> members = new java.util.ArrayList<>();
			if (obj.getEncoding() == org.example.model.Encoding.INTSET) {
				org.example.model.Intset intset = (org.example.model.Intset) value;
				for (Long l : intset.getAll()) {
					members.add(l.toString());
				}
			} else {
				java.util.Set<String> set = (java.util.Set<String>) value;
				members.addAll(set);
			}

			if (members.isEmpty())
				return;

			StringBuilder line = new StringBuilder();
			line.append("SADD ").append(key);
			for (String member : members) {
				line.append(' ').append(member);
			}
			writer.write(line.toString());
			writer.newLine();
		} else {
			// Default to SET for strings/other
			String v = String.valueOf(value);
			StringBuilder line = new StringBuilder();
			line.append("SET ").append(key).append(' ').append(v);
			if (remainingSec != -1) {
				line.append(' ').append("EX ").append(remainingSec);
			}
			writer.write(line.toString());
			writer.newLine();
		}

		if (remainingSec != -1 && obj.getType() == org.example.model.ValueType.SET) {
			// Sets need a separate EXPIRE command if they have expiry
			writer.write("EXPIRE " + key + " " + remainingSec);
			writer.newLine();
		}
	}
}
