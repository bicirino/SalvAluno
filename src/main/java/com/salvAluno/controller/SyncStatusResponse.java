package com.salvAluno.controller;

public record SyncStatusResponse(boolean running, String message, long taskCount) {}
