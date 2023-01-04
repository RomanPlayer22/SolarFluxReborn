package org.zeith.solarflux;

import net.minecraft.client.renderer.model.ModelResourceLocation;
import net.minecraft.command.Commands;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.*;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.zeith.hammerlib.client.adapter.ResourcePackAdapter;
import org.zeith.hammerlib.core.adapter.LanguageAdapter;
import org.zeith.hammerlib.util.mcf.ScanDataHelper;
import org.zeith.solarflux.client.SolarFluxResourcePack;
import org.zeith.solarflux.client.SolarPanelBakedModel;
import org.zeith.solarflux.compat.ISFCompat;
import org.zeith.solarflux.compat.SFCompat;
import org.zeith.solarflux.items.ItemsSF;
import org.zeith.solarflux.net.SFNetwork;
import org.zeith.solarflux.panels.SolarPanels;
import org.zeith.solarflux.proxy.SFRClientProxy;
import org.zeith.solarflux.proxy.SFRCommonProxy;

import java.util.*;
import java.util.function.Consumer;

@Mod("solarflux")
public class SolarFlux
{
	public static final Logger LOG = LogManager.getLogger("SolarFlux");
	public static final SFRCommonProxy PROXY = DistExecutor.unsafeRunForDist(() -> SFRClientProxy::new, () -> SFRCommonProxy::new);
	public static final ItemGroup ITEM_GROUP = new ItemGroup(InfoSF.MOD_ID)
	{
		@Override
		public ItemStack makeIcon()
		{
			return new ItemStack(ItemsSF.PHOTOVOLTAIC_CELL_3);
		}
	};
	public static final String MOD_ID = "solarflux";
	
	private static final List<ISFCompat> COMPATS = new ArrayList<>();
	
	public SolarFlux()
	{
		FMLJavaModLoadingContext.get().getModEventBus().register(this);
		
		LanguageAdapter.registerMod("solarflux");
		
		SolarPanels.init();
		
		for(ScanDataHelper.ModAwareAnnotationData scan : ScanDataHelper.lookupAnnotatedObjects(SFCompat.class))
		{
			String modid = Objects.toString(scan.getProperty("value").orElse(null));
			if(ModList.get().isLoaded(modid))
			{
				try
				{
					ISFCompat compat = scan.getOwnerClass().asSubclass(ISFCompat.class).getDeclaredConstructor().newInstance();
					compat.construct();
					COMPATS.add(compat);
					compat.setupConfigFile(FMLPaths.CONFIGDIR.get().resolve("solarflux").resolve("compat").resolve(modid + ".hlc").toFile());
					LOG.info("Added " + ModList.get().getModContainerById(modid).map(ModContainer::getModInfo).map(IModInfo::getDisplayName).orElse(modid) + " compatibility to Solar Flux.");
				} catch(Throwable e)
				{
					e.printStackTrace();
				}
			}
		}
		
		processCompats(ISFCompat::registerPanels);
		ResourcePackAdapter.registerResourcePack(SolarFluxResourcePack.getPackInstance());
	}
	
	public static ResourceLocation id(String s)
	{
		return new ResourceLocation(MOD_ID, s);
	}
	
	@SubscribeEvent
	public void commonSetup(FMLCommonSetupEvent e)
	{
		PROXY.commonSetup();
		SFNetwork.init();
	}
	
	@SubscribeEvent
	public void loadComplete(FMLLoadCompleteEvent e)
	{
		SolarPanels.refreshConfigs();
	}
	
	@SubscribeEvent
	@OnlyIn(Dist.CLIENT)
	public void clientSetup(FMLClientSetupEvent e)
	{
		PROXY.clientSetup();
	}
	
	@SubscribeEvent
	@OnlyIn(Dist.CLIENT)
	public void modelBake(ModelBakeEvent e)
	{
		SolarPanels.listPanelBlocks().forEach(spb -> e.getModelRegistry().put(new ModelResourceLocation(spb.getRegistryName(), ""), new SolarPanelBakedModel(spb)));
	}
	
	public static void processCompats(Consumer<ISFCompat> handler)
	{
		COMPATS.forEach(handler);
	}
	
	@EventBusSubscriber(bus = Bus.FORGE)
	public static class Registration
	{
		@SubscribeEvent
		public static void startServer(FMLServerStartingEvent e)
		{
			e.getServer().getCommands().getDispatcher().register(
					Commands.literal("solarflux")
							.then(Commands.literal("reload")
									.executes(src ->
											{
												SolarPanels.refreshConfigs();
												src.getSource().getServer().getPlayerList().getPlayers().forEach(SFNetwork::sendAllPanels);
												return 1;
											}
									)
							)
			);
		}
	}
}