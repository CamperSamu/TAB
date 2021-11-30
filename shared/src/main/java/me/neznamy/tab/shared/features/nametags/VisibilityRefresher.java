package me.neznamy.tab.shared.features.nametags;

import java.util.Arrays;

import me.neznamy.tab.api.TabFeature;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.shared.TAB;

public class VisibilityRefresher extends TabFeature {

	private final NameTag nametags;

	public VisibilityRefresher(NameTag nametags) {
		super(nametags.getFeatureName(), "Updating nametag visibility");
		this.nametags = nametags;
		TAB.getInstance().getPlaceholderManager().registerPlayerPlaceholder("%invisible%", 500, p -> p.hasInvisibilityPotion());
		addUsedPlaceholders(Arrays.asList("%invisible%"));
	}

	@Override
	public void refresh(TabPlayer p, boolean force) {
		nametags.updateTeamData(p);
	}
}