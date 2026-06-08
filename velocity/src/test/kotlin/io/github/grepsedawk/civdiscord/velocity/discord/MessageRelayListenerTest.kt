package io.github.grepsedawk.civdiscord.velocity.discord

import io.github.grepsedawk.civdiscord.core.db.Binding
import io.github.grepsedawk.civdiscord.core.db.BindingDao
import io.github.grepsedawk.civdiscord.core.db.Relay
import io.github.grepsedawk.civdiscord.core.db.RelayDao
import io.github.grepsedawk.civdiscord.velocity.chat.ChatRelay
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.Executor

class MessageRelayListenerTest {

    private val groupName = "grepsedawk"
    private val channelId = 9000L
    private val mcUuid = UUID.fromString("0111b95d-110c-4ea1-b4b2-59afeff296f4")
    private val mcName = "grepsedawk"
    private val discordId = 42L

    private data class Fixture(
        val listener: MessageRelayListener,
        val relays: RelayDao,
        val bindings: BindingDao,
        val webhook: WebhookRelay,
        val linkPrompt: LinkPrompt,
        val chatRelay: ChatRelay,
        val unbinds: MutableList<Long>,
        val notifier: WriterlessChannelNotifier,
    )

    private class PermissivePermService : NameLayerPermService(lookup = { _, _, _ -> PermCheck.DENIED }) {
        override fun check(mcUuid: UUID, group: String, perm: String): PermCheck = PermCheck.ALLOWED
    }

    private class FakePermService(
        private val readChat: Map<Pair<UUID, String>, Boolean> = emptyMap(),
    ) : NameLayerPermService(lookup = { _, _, _ -> PermCheck.DENIED }) {
        override fun check(mcUuid: UUID, group: String, perm: String): PermCheck = if (perm == NameLayerPermService.READ_CHAT && (readChat[mcUuid to group] ?: false)) PermCheck.ALLOWED else PermCheck.DENIED
    }

    private class UnknownPermService : NameLayerPermService(lookup = { _, _, _ -> PermCheck.UNKNOWN }) {
        override fun check(mcUuid: UUID, group: String, perm: String): PermCheck = PermCheck.UNKNOWN
    }

    private fun event(
        isBot: Boolean = false,
        isWebhook: Boolean = false,
        fromGuild: Boolean = true,
        chId: Long = channelId,
        authorId: Long = discordId,
        content: String = "hello",
    ): MessageReceivedEvent {
        val e = mockk<MessageReceivedEvent>(relaxed = true)
        val author = mockk<User>(relaxed = true)
        every { author.isBot } returns isBot
        every { author.idLong } returns authorId
        every { e.author } returns author
        every { e.isWebhookMessage } returns isWebhook
        every { e.isFromGuild } returns fromGuild
        val channel = mockk<MessageChannelUnion>(relaxed = true)
        every { channel.idLong } returns chId
        every { e.channel } returns channel
        val msg = mockk<Message>(relaxed = true)
        every { msg.contentDisplay } returns content
        every { e.message } returns msg
        every { msg.delete() } returns mockk<AuditableRestAction<Void>>(relaxed = true)
        return e
    }

    private val sameThread = Executor { it.run() }

    private fun setup(
        linked: Boolean,
        permService: NameLayerPermService = PermissivePermService(),
        seedBindings: List<Relay> = listOf(
            Relay(
                guildId = 100L,
                namelayerGroup = "townhall",
                discordChannelId = channelId,
                isWriter = true,
                showSnitches = false,
                chatFormat = null,
                snitchPing = null,
                createdBy = 5L,
                createdAt = 0L,
            ),
        ),
        notifier: WriterlessChannelNotifier = WriterlessChannelNotifier(send = { _, _ -> }),
    ): Fixture {
        val relays = mockk<RelayDao>(relaxed = true)
        every { relays.listForChannel(channelId) } returns seedBindings
        val bindings = mockk<BindingDao>(relaxed = true)
        every { bindings.findByDiscordId(discordId) } returns
            if (linked) Binding(discordId, mcUuid, mcName, 0L) else null
        val webhook = mockk<WebhookRelay>(relaxed = true)
        val linkPrompt = mockk<LinkPrompt>(relaxed = true)
        val chatRelay = mockk<ChatRelay>(relaxed = true)
        val unbinds = mutableListOf<Long>()
        return Fixture(
            MessageRelayListener(
                relays = relays,
                bindings = bindings,
                webhook = webhook,
                linkPrompt = linkPrompt,
                chatRelay = chatRelay,
                permService = permService,
                unbind = { ch, _ -> unbinds.add(ch) },
                writerlessNotifier = notifier,
                worker = sameThread,
            ),
            relays,
            bindings,
            webhook,
            linkPrompt,
            chatRelay,
            unbinds,
            notifier,
        )
    }

    @Test
    fun `bot message is ignored`() {
        val f = setup(linked = true)
        f.listener.onMessageReceived(event(isBot = true))
        verify(exactly = 0) { f.webhook.send(any(), any(), any(), any()) }
        verify(exactly = 0) { f.linkPrompt.notify(any(), any()) }
        verify(exactly = 0) { f.chatRelay.fromDiscord(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `webhook message is ignored (no loop)`() {
        val f = setup(linked = true)
        f.listener.onMessageReceived(event(isWebhook = true))
        verify(exactly = 0) { f.webhook.send(any(), any(), any(), any()) }
        verify(exactly = 0) { f.chatRelay.fromDiscord(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `non-relay channel is ignored`() {
        val relays = mockk<RelayDao>(relaxed = true)
        every { relays.listForChannel(any()) } returns emptyList()
        val bindings = mockk<BindingDao>(relaxed = true)
        val webhook = mockk<WebhookRelay>(relaxed = true)
        val linkPrompt = mockk<LinkPrompt>(relaxed = true)
        val chatRelay = mockk<ChatRelay>(relaxed = true)
        val l = MessageRelayListener(
            relays = relays,
            bindings = bindings,
            webhook = webhook,
            linkPrompt = linkPrompt,
            chatRelay = chatRelay,
            permService = PermissivePermService(),
            unbind = { _, _ -> },
            writerlessNotifier = WriterlessChannelNotifier(send = { _, _ -> }),
            worker = sameThread,
        )
        l.onMessageReceived(event())
        verify(exactly = 0) { webhook.send(any(), any(), any(), any()) }
        verify(exactly = 0) { linkPrompt.notify(any(), any()) }
        verify(exactly = 0) { chatRelay.fromDiscord(any(), any(), any(), any()) }
    }

    @Test
    fun `linked user deletes, webhooks, and bridges`() {
        val f = setup(linked = true)
        val e = event(content = "hi")
        val msg = e.message
        f.listener.onMessageReceived(e)
        verify(exactly = 1) { msg.delete() }
        verify(exactly = 1) {
            f.webhook.send(
                channelId,
                "$mcName [townhall]",
                SkinUrl.avatar(mcUuid),
                "hi",
            )
        }
        verify(exactly = 1) {
            f.chatRelay.fromDiscord(
                channelId = channelId,
                fromDisplay = mcName,
                fromUuid = mcUuid.toString(),
                text = "hi",
                preComputedGroup = "townhall",
                discordId = discordId,
            )
        }
        verify(exactly = 0) { f.linkPrompt.notify(any(), any()) }
    }

    @Test
    fun `unlinked user deletes and prompts to link`() {
        val f = setup(linked = false)
        val e = event()
        val msg = e.message
        val channel = e.channel
        f.listener.onMessageReceived(e)
        verify(exactly = 1) { msg.delete() }
        verify(exactly = 0) { f.webhook.send(any(), any(), any(), any()) }
        verify(exactly = 1) { f.linkPrompt.notify(discordId, channel) }
        verify(exactly = 0) { f.chatRelay.fromDiscord(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `attachment-only message is left alone`() {
        val f = setup(linked = true)
        val e = event(content = "")
        val msg = e.message
        f.listener.onMessageReceived(e)
        verify(exactly = 0) { msg.delete() }
        verify(exactly = 0) { f.webhook.send(any(), any(), any(), any()) }
        verify(exactly = 0) { f.chatRelay.fromDiscord(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { f.linkPrompt.notify(any(), any()) }
    }

    @Test
    fun `linked sender who is not a group member triggers unbind and skips webhook`() {
        val f = setup(linked = true, permService = FakePermService())
        f.listener.onMessageReceived(event(content = "hi"))
        f.unbinds shouldBe listOf(channelId)
        verify(exactly = 0) { f.webhook.send(any(), any(), any(), any()) }
        verify(exactly = 0) { f.chatRelay.fromDiscord(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `UNKNOWN perm check keeps the binding and skips webhook`() {
        val f = setup(linked = true, permService = UnknownPermService())
        f.listener.onMessageReceived(event(content = "hi"))
        f.unbinds.isEmpty() shouldBe true
        verify(exactly = 0) { f.webhook.send(any(), any(), any(), any()) }
        verify(exactly = 0) { f.chatRelay.fromDiscord(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `writerless channel posts public hint via notifier`() {
        val notices = mutableListOf<Pair<Long, List<String>>>()
        val notifier =
            WriterlessChannelNotifier(
                send = { ch, text ->
                    notices += ch to Regex("`([^`]+)`").findAll(text).map { it.groupValues[1] }.toList()
                },
            )
        val f =
            setup(
                linked = true,
                seedBindings =
                listOf(
                    Relay(
                        guildId = 100L,
                        namelayerGroup = "townhall",
                        discordChannelId = channelId,
                        isWriter = false,
                        showSnitches = false,
                        chatFormat = null,
                        snitchPing = null,
                        createdBy = 5L,
                        createdAt = 0L,
                    ),
                    Relay(
                        guildId = 100L,
                        namelayerGroup = "tavern",
                        discordChannelId = channelId,
                        isWriter = false,
                        showSnitches = false,
                        chatFormat = null,
                        snitchPing = null,
                        createdBy = 5L,
                        createdAt = 0L,
                    ),
                ),
                notifier = notifier,
            )
        f.listener.onMessageReceived(event())
        notices.first().first shouldBe channelId
        notices.first().second.toSet() shouldBe setOf("townhall", "tavern")
    }

    @Test
    fun `channel with no bindings emits nothing`() {
        val notices = mutableListOf<Long>()
        val notifier = WriterlessChannelNotifier(send = { ch, _ -> notices += ch })
        val f = setup(linked = true, seedBindings = emptyList(), notifier = notifier)
        f.listener.onMessageReceived(event())
        notices shouldBe emptyList()
    }

    @Test
    fun `writer present forwards through chatRelay`() {
        val f = setup(linked = true) // default seed = single writer row
        f.listener.onMessageReceived(event())
        verify { f.chatRelay.fromDiscord(channelId, any(), any(), "hello", "townhall", any()) }
    }
}
