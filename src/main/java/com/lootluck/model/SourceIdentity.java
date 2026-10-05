package com.lootluck.model;

import java.util.Objects;

/** Original source identity is retained; names alone never merge NPC variants or worlds. */
public final class SourceIdentity
{
	public final String type, name, variant, context;
	public SourceIdentity(String type, String name, String variant, String context)
	{
		this.type = Objects.requireNonNull(type);
		this.name = Objects.requireNonNull(name);
		this.variant = Objects.requireNonNull(variant);
		this.context = Objects.requireNonNull(context);
	}
	public String key() { return type + "\u001f" + name + "\u001f" + variant + "\u001f" + context; }
}
