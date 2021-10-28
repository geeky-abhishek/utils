package com.uci.utils.bot.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BotUtil {
	public static String botEnabledStatus = "enabled";
	public static String botLiveStatus = "live";

	public static Boolean checkBotValidFromJsonNode(JsonNode root) {
		JsonNode data = root.path("result").path("data");
    	
    	String status = data.findValue("status").asText();
    	String startDate = data.findValue("startDate").asText();
    	
    	Boolean result = checkBotValid(status, startDate);
    	log.info("Bot check result: "+result);
    	return result;
	}
	
	public static Boolean checkBotValid(String status, String startDate) {
		if(checkBotLiveStatus(status) && checkBotStartDateValid(startDate)) {
			return true;
		}
		return false;
	}
	
	public static Boolean checkBotLiveStatus(String status) {
		status = status.toLowerCase();
		if(status.equals(botLiveStatus) || status.equals(botEnabledStatus)) {
			return true;
		} else {
			log.error("Bot is not enabled, Please enable it.");
		}
		return false;
	}
	
	public static Boolean checkBotStartDateValid(String startDate) {
		try {
			/* Start Date  */
			if(startDate == null || startDate == "null" || startDate.isEmpty()) {
				return true;
			}
			
			DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        	
        	/* local date time */
        	LocalDateTime localNow = LocalDateTime.now();
        	String dateString = fmt.format(localNow).toString();
        	LocalDateTime localDateTime = LocalDateTime.parse(dateString, fmt);
        	
        	/* bot start date in local date time format */
        	LocalDateTime localStartDate = LocalDateTime.parse(startDate, fmt);
            
        	if(localDateTime.compareTo(localStartDate) >= 0) {
        		return true;
        	}
		} catch (Exception e) {
			log.error("Error in checkBotStartDateValid: "+e.getMessage());
		}
		log.error("Bot starting date is not valid.");
		return false;
	}
}
