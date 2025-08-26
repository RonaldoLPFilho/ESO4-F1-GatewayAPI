package com.example.gatewayapi.adapters.inbound.dto;

public record WebCamFrameRequest (
        String imageBase64,
        String fileName
){ }
