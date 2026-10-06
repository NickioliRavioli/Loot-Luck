package com.lootluck.rates;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.lootluck.model.ObservationContext;
import com.lootluck.model.SourceIdentity;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/** Same-named NPCs resolve by public Wiki IDs; ambiguous IDs are absent from this index. */
public final class WikiSourceIndex
{
	private final JsonObject owners;
	public WikiSourceIndex(Gson gson)
	{
		try(Reader reader=new InputStreamReader(java.util.Objects.requireNonNull(getClass().getResourceAsStream("/rates/source-identities.json")),StandardCharsets.UTF_8))
		{ owners=gson.fromJson(reader,JsonObject.class); }
		catch(java.io.IOException ex) { throw new IllegalStateException("Cannot read Wiki source identities",ex); }
	}
	public SourceIdentity resolve(SourceIdentity source)
	{
		if(!"NPC".equals(source.type)) { return source; }
		Integer id=ObservationContext.from(source).npcId;
		if(id==null||!owners.has(id.toString())) { return source; }
		String owner=owners.get(id.toString()).getAsString();
		return new SourceIdentity(source.type,source.name,source.variant,source.context,owner.equals(source.name)?"":owner,source.deathLocation==null?"":source.deathLocation);
	}
}
