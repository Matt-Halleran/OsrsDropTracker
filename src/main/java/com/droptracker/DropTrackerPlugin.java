package com.droptracker;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.Player;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;
import okhttp3.OkHttpClient;

import javax.inject.Inject;
import java.util.Collection;
import java.util.regex.Pattern;

@Slf4j
@PluginDescriptor(
	name = "Example"
)
public class DropTrackerPlugin extends Plugin
{
	private final DropEventHandler _dropEventHandler;

	@Inject
	private Client client;

	@Inject
	private DropTrackerConfig config;

	@Inject
	private ItemManager itemManager;

	@Inject
	private OkHttpClient okHttpClient;

	private static final Pattern PICKPOCKET_REGEX = Pattern.compile("You pick (the )?(?<target>.+)'s? pocket.*");

	private int pickpocketTick = -1;

	public DropTrackerPlugin()
	{
		_dropEventHandler = new DropEventHandler(itemManager, okHttpClient, client);
	}

	@Override
	protected void startUp() throws Exception
	{
		log.debug("Example started!");
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.debug("Example stopped!");
	}

	@Subscribe
	public void onServerNpcLoot(ServerNpcLoot serverNpcLoot)
	{
		boolean isPickpocket = pickpocketTick == client.getTickCount();

		if (isPickpocket)
		{
			_dropEventHandler.HandlePickpocketDrop(serverNpcLoot);
		}

		_dropEventHandler.HandleNpcDrop(serverNpcLoot);
	}

	@Subscribe
	public void onLootReceived(LootReceived lootReceived)
	{
		LootRecordType lootType = lootReceived.getType();

		switch (lootType){
			case EVENT:
				_dropEventHandler.HandleEventDrop(lootReceived);
			break;

			case UNKNOWN:
				_dropEventHandler.HandleUnknownDrop(lootReceived);
			break;

            default:
			break;
		}
	}

	//ServerNpcLoot

	@Provides
	DropTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DropTrackerConfig.class);
	}
}
