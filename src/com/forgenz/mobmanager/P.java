/*
 * Copyright 2013 Michael McKnight. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 *    1. Redistributions of source code must retain the above copyright notice, this list of
 *       conditions and the following disclaimer.
 *
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list
 *       of conditions and the following disclaimer in the documentation and/or other materials
 *       provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ''AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE AUTHOR OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation are those of the
 * authors and contributors and should not be interpreted as representing official policies,
 * either expressed or implied, of anybody else.
 */

package com.forgenz.mobmanager;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

import com.forgenz.mobmanager.config.Config;
import com.forgenz.mobmanager.listeners.AttributeMobListener;
import com.forgenz.mobmanager.listeners.ChunkListener;
import com.forgenz.mobmanager.listeners.MobListener;
import com.forgenz.mobmanager.listeners.PlayerListener;
import com.forgenz.mobmanager.listeners.commands.MMCommandListener;
import com.forgenz.mobmanager.tasks.MobDespawnTask;
import com.forgenz.mobmanager.util.AnimalProtection;
import com.forgenz.mobmanager.world.MMWorld;

/**
 * <b>MobManager</b> </br>
 * MobManager aims to reduce the number of unnecessary mob spawns </br>
 * 
 * @author Michael McKnight (ShadowDog007)
 *
 */
public class P extends JavaPlugin
{
	public static P p = null;
	public static FileConfiguration cfg = null;
	
	public static ConcurrentHashMap<String, MMWorld> worlds = null;
	
	private MobDespawnTask despawner = null;
	
	public AnimalProtection animalProtection = null;

	@Override
	public void onLoad()
	{
	}

	@Override
	public void onEnable()
	{
		p = this;
		cfg = getConfig();
		
		// Start Metrics gathering
		try
		{
			Metrics metrics = new Metrics(this);
			metrics.start();
		}
		catch (IOException e)
		{
			getLogger().info("Failed to start metrics gathering..  :(");
		}
		
		// Load config
		Config config = new Config();

		// Setup worlds
		worlds = new ConcurrentHashMap<String, MMWorld>(2, 0.75F, 2);
		if (config.setupWorlds() == 0)
		{
			getLogger().warning("No valid worlds found");
			getServer().getPluginManager().disablePlugin(this);
			return;
		}
		
		// Register Mob event listeners
		getServer().getPluginManager().registerEvents(new MobListener(), this);
		getServer().getPluginManager().registerEvents(new AttributeMobListener(), this);
		// Register Player event listener
		getServer().getPluginManager().registerEvents(new PlayerListener(), this);
		// Register Chunk event listener
		getServer().getPluginManager().registerEvents(new ChunkListener(), this);
		
		// Register MobManager command
		getCommand("mm").setExecutor(new MMCommandListener());
		
		// Start the despawner task
		despawner = new MobDespawnTask();
		despawner.runTaskTimer(this, 1L, Config.ticksPerDespawnScan);
		
		// Setup animal protection
		if (Config.enableAnimalDespawning)
		{
			animalProtection = new AnimalProtection();
			if (animalProtection != null)
			{
				getServer().getPluginManager().registerEvents(animalProtection, this);
				animalProtection.runTaskTimerAsynchronously(this, Config.protectedFarmAnimalSaveInterval, Config.protectedFarmAnimalSaveInterval);
			}
		}
		
		// Sets already living mob HP to config settings
		boolean hasGlobal = Config.mobAbilities.size() != 0;
		boolean addAbilities = hasGlobal;
		
		// If there are no global configs we check if there are any world configs
		if (!hasGlobal)
		{
			for (MMWorld world : worlds.values())
			{
				if (world.worldConf.mobAbilities.size() != 0)
				{
					addAbilities = true;
					break;
				}
			}
		}
		
		// Check if we should bother iterating through entities
		if (addAbilities)
		{
			// Iterate through each enabled world
			for (MMWorld world : worlds.values())
			{
				// If there are no global configs and the world has no configs, check next world
				if (!hasGlobal && world.worldConf.mobAbilities.size() == 0)
					continue;
				
				// Iterate through each entity in the world and set their max HP accordingly
				for (LivingEntity entity : world.getWorld().getLivingEntities())
				{
					AttributeMobListener.addAbilities(entity);
				}
			}
		}
		
		getLogger().info("v" + getDescription().getVersion() + " ennabled with " + worlds.size() + " worlds");
		// And we are done :D
	}

	@Override
	public void onDisable()
	{
		// Resets mob abilities
		boolean hasGlobal = Config.mobAbilities.size() != 0;
		boolean removeAbilities = hasGlobal;
		
		// If there are no global configs we check if there are any world configs
		if (!hasGlobal)
		{
			for (MMWorld world : worlds.values())
			{
				if (world.worldConf.mobAbilities.size() != 0)
				{
					removeAbilities = true;
					break;
				}
			}
		}
		
		// Check if we should bother iterating through entities
		if (removeAbilities)
		{
			// Iterate through each enabled world
			for (MMWorld world : worlds.values())
			{
				// If there are no global configs and the world has no configs, check next world
				if (!hasGlobal && world.worldConf.mobAbilities.size() == 0)
					continue;
				
				// Iterate through each entity in the world and set their max HP accordingly
				for (LivingEntity entity : world.getWorld().getLivingEntities())
				{
					AttributeMobListener.removeAbilities(entity);
				}
			}
		}
		
		
		// This has not worked for me in the past..
		getServer().getScheduler().cancelTasks(this);
		// Soo....
		if (despawner != null)
			despawner.cancel();
		
		if (animalProtection != null)
		{
			animalProtection.cancel();
			animalProtection.run();
		}
		
		p = null;
		cfg = null;
		worlds = null;
	}
}
