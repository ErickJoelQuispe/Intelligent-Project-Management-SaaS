package com.epm.user.domain.port.in.command;

/**
 * Command to create a new team.
 */
public record CreateTeamCommand(String name, String description) {
}
