package com.namelessmc.bot.listeners;

import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.namelessmc.bot.Language;
import com.namelessmc.bot.Language.Term;
import com.namelessmc.bot.Main;
import com.namelessmc.bot.connections.BackendStorageException;
import com.namelessmc.java_api.NamelessAPI;
import com.namelessmc.java_api.NamelessException;
import com.namelessmc.java_api.NamelessVersion;
import com.namelessmc.java_api.Website;
import com.namelessmc.java_api.exception.UnknownNamelessVersionException;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class GuildJoinHandler extends ListenerAdapter {

	private static final Logger LOGGER = LoggerFactory.getLogger("Guild join listener");
	private static final String API_URL_COMMAND = "/apiurl";

	@Override
	public void onGuildJoin(final GuildJoinEvent event) {
		LOGGER.info("Joined guild '{}'", event.getGuild().getName());

		final Language language = Language.getGuildLanguage(event.getGuild());

		event.getJDA().retrieveUserById(event.getGuild().getOwnerIdLong()).flatMap(User::openPrivateChannel).queue(channel -> {
			Optional<NamelessAPI> optApi;
			try {
				optApi = Main.getConnectionManager().getApi(event.getGuild().getIdLong());
			} catch (final BackendStorageException e) {
				LOGGER.error("Storage error during guild join", e);
				return;
			}


			final long guildId = event.getGuild().getIdLong();

			if (optApi.isEmpty()) {
				channel.sendMessage(language.get(Term.GUILD_JOIN_SUCCESS, "command", API_URL_COMMAND, "guildId", guildId))
						.queue(message -> LOGGER.info("Sent new join message to {} for guild {}",
								channel.getUser().getName(), event.getGuild().getName()));
			} else {
				try {
					final NamelessAPI api = optApi.get();
					final Website info = api.getWebsite();
					try {
						if (Main.SUPPORTED_WEBSITE_VERSIONS.contains(info.getParsedVersion())) {
							// Good to go
							channel.sendMessage(language.get(Term.GUILD_JOIN_WELCOME_BACK, "command", API_URL_COMMAND, "guildId", guildId)).queue();
						} else {
							// Incompatible version
							final String supportedVersions = Main.SUPPORTED_WEBSITE_VERSIONS.stream().map(NamelessVersion::getName).collect(Collectors.joining(", "));
							channel.sendMessage(language.get(Term.ERROR_WEBSITE_VERSION, "version", info.getVersion(), "compatibleVersions", supportedVersions)).queue();
						}
					} catch (final UnknownNamelessVersionException e) {
						// API doesn't recognize this version, but we can still display the unparsed name
						final String supportedVersions = Main.SUPPORTED_WEBSITE_VERSIONS.stream().map(NamelessVersion::getName).collect(Collectors.joining(", "));
						channel.sendMessage(language.get(Term.ERROR_WEBSITE_VERSION, "version", info.getVersion(), "compatibleVersions", supportedVersions)).queue();
					}
				} catch (final NamelessException e) {
					// Error with their stored url. Make them update the url
					channel.sendMessage(language.get(Term.GUILD_JOIN_NEEDS_RENEW, "command", API_URL_COMMAND, "guildId", guildId)).queue();
					LOGGER.info("Guild join, previously stored URL doesn't work");
				}
			}
		});

	}

}
