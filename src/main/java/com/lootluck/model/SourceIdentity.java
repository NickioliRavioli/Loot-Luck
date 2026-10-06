package com.lootluck.model;

import java.util.Objects;

/** Original source identity is retained; names alone never merge NPC variants or worlds. */
public final class SourceIdentity
{
	public final String type, name, variant, context, wikiOwner;
	public SourceIdentity(String type, String name, String variant, String context)
	{ this(type,name,variant,context,""); }
	public SourceIdentity(String type, String name, String variant, String context,String wikiOwner)
	{
		this.type = Objects.requireNonNull(type);
		this.name = Objects.requireNonNull(name);
		this.variant = Objects.requireNonNull(variant);
		this.context = Objects.requireNonNull(context);
		this.wikiOwner = Objects.requireNonNull(wikiOwner);
	}
	public String key() { return type + "\u001f" + name + "\u001f" + variant + "\u001f" + context + (wikiOwner==null||wikiOwner.isBlank()?"":"\u001f"+wikiOwner); }
	public SourceIdentity family() { return new SourceIdentity(type,name,"","",wikiOwner==null?"":wikiOwner); }
}
