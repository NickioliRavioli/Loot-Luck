package com.lootluck.rates;

import com.lootluck.model.SourceIdentity;
import java.util.List;

public interface RateProvider
{
	List<RateRow> rows(SourceIdentity source);
	void refresh(SourceIdentity source, boolean force);
}
