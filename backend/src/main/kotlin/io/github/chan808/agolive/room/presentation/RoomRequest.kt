package io.github.chan808.agolive.room.presentation

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateRoomRequest(
    @field:NotBlank @field:Size(max = 100)
    val name: String,
    val isPrivate: Boolean = false,
    @field:Min(1) @field:Max(100)
    val maxCapacity: Int = 10,
)

data class UpdateRoomRequest(
    @field:Size(max = 100)
    val name: String? = null,
    val isPrivate: Boolean? = null,
    @field:Min(1) @field:Max(100)
    val maxCapacity: Int? = null,
)
