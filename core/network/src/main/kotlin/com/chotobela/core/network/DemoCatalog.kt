package com.chotobela.core.network

import com.chotobela.core.network.dto.GameDto
import com.chotobela.core.network.dto.ProfileDto
import com.chotobela.core.network.dto.ReviewDto

/**
 * Seeded catalog used in DEMO MODE (no Supabase credentials).
 * Represents what the backend `games` table will serve once provisioned.
 *
 * The two bundled titles are ORIGINAL Chotobela homebrew CHIP-8 games,
 * hand-assembled and verified with tools/chip8 (assembler + simulator).
 */
object DemoCatalog {

    val games: List<GameDto> = listOf(
        GameDto(
            id = "chotobela-paddle",
            title = "Chotobela Paddle",
            description = "Catch the bouncing ball with your paddle. Every catch beeps and raises your score. Keys: 4 = left, 6 = right. An original Chotobela homebrew built for this launch.",
            platform = Platform.CHIP8.label,
            core = Core.CHIP8.id,
            coverImage = null,
            downloadUrl = "asset://roms/chip8/PADDLE.ch8",
            size = 172L,
            developer = "Chotobela Homebrew",
            year = 2026,
            rating = 4.5,
            downloadCount = 1204,
            featured = true,
            trending = true,
            category = "arcade"
        ),
        GameDto(
            id = "chotobela-bounce",
            title = "Neon Bounce",
            description = "A hypnotic screensaver-style demo: a glowing block ricochets across the phosphor grid forever. Perfect first test for your emulator setup.",
            platform = Platform.CHIP8.label,
            core = Core.CHIP8.id,
            coverImage = null,
            downloadUrl = "asset://roms/chip8/BOUNCE.ch8",
            size = 80L,
            developer = "Chotobela Homebrew",
            year = 2026,
            rating = 4.0,
            downloadCount = 892,
            featured = true,
            trending = false,
            category = "demo"
        ),
        GameDto(
            id = "chotobela-paddle-duel",
            title = "Paddle Duel",
            description = "The classic paddle experience with a fresh coat of amber phosphor. Compete for the high score in your household.",
            platform = Platform.CHIP8.label,
            core = Core.CHIP8.id,
            coverImage = null,
            downloadUrl = "asset://roms/chip8/PADDLE.ch8",
            size = 172L,
            developer = "Chotobela Homebrew",
            year = 2026,
            rating = 4.2,
            downloadCount = 611,
            featured = false,
            trending = true,
            category = "sports"
        )
    )

    val reviews: Map<String, List<ReviewDto>> = emptyMap()

    val demoProfile: ProfileDto = ProfileDto(
        id = "local-user",
        username = "RetroPlayer",
        avatar = null
    )

    enum class Platform(val label: String) {
        ARCADE("Arcade"), NES("NES"), SNES("SNES"), GB("Game Boy"),
        GBA("GBA"), GENESIS("Genesis"), PSX("PlayStation"), CHIP8("CHIP-8")
    }

    enum class Core(val id: String) {
        MAME("mame"), FBNEO("fbneo"), CHIP8("chip8")
    }
}
