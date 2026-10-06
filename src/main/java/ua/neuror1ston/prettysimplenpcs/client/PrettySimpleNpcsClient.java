package ua.neuror1ston.prettysimplenpcs.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.neuror1ston.prettysimplenpcs.PrettySimpleNpcsMod;

public class PrettySimpleNpcsClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("prettysimplenpcs-client");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Pretty Simple NPCs Client...");

        // Register custom player-model NPC renderer with scale, custom skin, and idle barks
        EntityRendererRegistry.register(PrettySimpleNpcsMod.SIMPLE_NPC_ENTITY_TYPE, SimpleNpcEntityRenderer::new);

        // Register client packet receivers
        ClientNetwork.registerClientReceivers();

        LOGGER.info("Pretty Simple NPCs Client successfully initialized!");
    }
}
