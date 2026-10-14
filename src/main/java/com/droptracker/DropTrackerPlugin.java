package com.droptracker;

import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarbitID;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.util.Text;
import net.runelite.http.api.loottracker.LootRecordType;

import javax.inject.Inject;
import java.util.concurrent.*;
import java.util.regex.Pattern;

@Slf4j
@PluginDescriptor(
	name = "Drop Tracker"
)
public class DropTrackerPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private DropEventHandler dropEventHandler;

	@Inject
	private ScheduledExecutorService flushService;

	private static final Pattern PICKPOCKET_REGEX = Pattern.compile("You pick (the )?(?<target>.+)'s? pocket.*");
	private static final String COLLECTION_LOG_TEXT = "New item added to your collection log: ";

	private int pickpocketTick = -1;
	private ScheduledFuture<?> flushTask;

	public DropTrackerPlugin()
	{
	}

	@Override
	protected void startUp() throws Exception
	{
		flushTask = flushService.scheduleAtFixedRate(dropEventHandler::flush, 30, 30, TimeUnit.SECONDS);
	}

	@Override
	protected void shutDown() throws Exception
	{
		if (flushTask != null)
		{
			flushTask.cancel(false);
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage e) {
		ChatMessageType type = e.getType();
		if (type != ChatMessageType.GAMEMESSAGE
				&& type != ChatMessageType.SPAM
				&& type != ChatMessageType.MESBOX) {
			return;
		}

		String message = e.getMessage();

		if (PICKPOCKET_REGEX.matcher(Text.removeTags(message)).matches())
		{
			pickpocketTick = client.getTickCount();
		}

		if (message.startsWith(COLLECTION_LOG_TEXT)
				&& (client.getVarbitValue(VarbitID.OPTION_COLLECTION_NEW_ITEM) & 1) == 1) {
			String entry = Text.removeTags(message).substring(COLLECTION_LOG_TEXT.length());
			dropEventHandler.handleCollectionLogEntry(entry);
		}
	}

	@Subscribe
	public void onServerNpcLoot(ServerNpcLoot serverNpcLoot)
	{
		boolean isPickpocket = pickpocketTick == client.getTickCount();

		if (isPickpocket)
		{
			dropEventHandler.handlePickpocketDrop(serverNpcLoot);
			return;
		}

		dropEventHandler.handleNpcDrop(serverNpcLoot);
	}

	@Subscribe
	public void onLootReceived(LootReceived lootReceived)
	{
		LootRecordType lootType = lootReceived.getType();

		switch (lootType){
			case EVENT:
				dropEventHandler.handleEventDrop(lootReceived);
			break;

			case UNKNOWN:
				dropEventHandler.handleUnknownDrop(lootReceived);
			break;

            default:
			break;
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged e) {
		if (e.getVarbitId() != VarbitID.OPTION_COLLECTION_NEW_ITEM) return;
		if ((client.getVarbitValue(VarbitID.OPTION_COLLECTION_NEW_ITEM) & 1) == 0) {
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
					"Drop Tracker: your collection log chat notification is off. Enable it in Settings > Chat so collection log drops get detected.",
					null);
		}
	}

	@Provides
	DropTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DropTrackerConfig.class);
	}
}
