
/*
 * PwnFilter -- Regex-based User Filter Plugin for Bukkit-based Minecraft servers.
 * Copyright (c) 2013 Pwn9.com. Tremor77 <admin@pwn9.com> / Sage905
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 */

package com.pwn9.PwnFilter.rules;

import com.pwn9.PwnFilter.DataCache;
import com.pwn9.PwnFilter.FilterState;
import com.pwn9.PwnFilter.util.LogManager;

import java.io.*;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;


/**
 * The RuleSet contains a compiled version of all the rules in the text file.
 * A RuleSet has a chain of Rules.  The ruleset apply method is unique to each
 * type of event, but the basic mechanism is:
 * <p>
 * Load a chain of rules from a text file, parse them into a chain
 * The Event Handler calls the RuleSet.appply() method with the signature of the event
 * The apply() method iterates over the rules, one at a time, matching, checking conditions,
 * and executing actions based on the message and the rules.
 *
 *
 * TODO: More documentation
 *
 * User: ptoal
 * Date: 13-04-05
 * Time: 12:38 PM
 */

public class RuleChain implements ChainEntry {
    enum ChainState {
        INIT,  // Chain was reset and needs to be reloaded before use.
        PARTIAL, // Chain is in the process of loading
        READY // Chain is fully loaded and ready to use.
    }

    private final RuleManager manager;
    private ChainState chainState;
    private ArrayList<ChainEntry> chain = new ArrayList<ChainEntry>();
    private final String configName;


    public RuleChain(RuleManager manager, String configName) {
        this.configName = configName;
        this.manager = manager;
        chainState = ChainState.INIT;
    }

    /**
     * (Re)load this rulechain's config from its file.
     * NOTE: If it has included rulechains, this will trigger a reload
     * of them as well.  The ChainState attribute should prevent infinite
     * recursion, even in the case where rules have been misconfigured.
     *
     * @return Success or failure
     */
    public boolean loadConfigFile() {
        chain = new ArrayList<ChainEntry>();
        File ruleFile = manager.getFile(configName);
        if (ruleFile != null) {
            try {
                if (parseRules(new FileReader(ruleFile))) {
                    DataCache.getInstance().addPermissions(getPermissionList());
                    return true;
                } else {
                    return false;
                }
            } catch (FileNotFoundException e) {
                return false;
            }
        } else return false;
    }

    public String getConfigName() { return configName;}

    public int ruleCount() {
        Integer count = 0;
        for (ChainEntry c : chain) {
            if (c instanceof RuleChain) {
                count += ((RuleChain) c).ruleCount();
            } else count++;
        }
        return count;
    }

    /**
     * Iterate over the chain in order, checking the Rule pattern against the
     * current message.  If the text pattern matches, test the rule conditions, to
     * ensure they are all met.  If all of the conditions are met, execute the Rule's
     * actions in sequential order.  If the Rule sets the stop=true of the FilterState,
     * stop processing rules.  If not, continue along the rule chain, checking the
     * (possibly modified) message against subsequent rules.
     *
     * @param state A FilterState object which is used to get information about
     *              this event, and update its status (eg: set cancelled)
     *
     */

    public void apply(FilterState state) throws IllegalStateException {

        if (chain == null) {
            throw new IllegalStateException("Chain is empty: " + configName);
        }

        for (ChainEntry entry : chain) {
            entry.apply(state);
            if (state.stop) {
                break;
            }
        }
    }

    public void execute(FilterState state ) {

        LogManager logManager = LogManager.getInstance();

        apply(state);

        if (state.pattern != null) {
            logManager.debugLogHigh("Debug last match: " + state.pattern.pattern());
            logManager.debugLogHigh("Debug original: " + state.getOriginalMessage().getColoredString());
            logManager.debugLogHigh("Debug current: " + state.message.getColoredString());
            logManager.debugLogHigh("Debug log: " + (state.log ? "yes" : "no"));
            logManager.debugLogHigh("Debug deny: " + (state.cancel ? "yes" : "no"));
        } else {
            logManager.debugLogHigh("[PwnFilter] Debug no match: " + state.getOriginalMessage().getColoredString());
        }

        if (state.cancel){
            state.addLogMessage("<"+state.playerName + "> Original message cancelled.");
        } else if (state.pattern != null) {
            state.addLogMessage("|" + state.listener.getShortName() + "| SENT <" +
                    state.playerName + "> " + state.message.getPlainString());
        }

        for (String s : state.getLogMessages()) {
            if (state.log) {
                LogManager.logger.log(LogManager.getRuleLogLevel(),s);
            } else {
                logManager.debugLogLow(s);
            }
        }
    }

    public boolean append(ChainEntry r) {
        if (r.isValid()) {
            chain.add(r); // Add the Rule to this chain
            return true;
        } else return false;
    }



    public boolean isValid() {
        return chainState == ChainState.READY;
    }

    /**
     * The DataCache object needs to know what permissions to cache.  Whenever this
     * rulechain is updated, the datacache should also be updated with the list of
     * permissions which are interesting.
     *
     * @return a Set of all permissions that this rule is interested in.
     */
    @Override
    public Set<String> getPermissionList() {
        TreeSet<String> permList = new TreeSet<String>();

        for (ChainEntry r : chain) {
            permList.addAll(r.getPermissionList());
        }
        return permList;
    }

    /**
     * Delete all rules in the chain, and reset its state to INIT
     */
    public void resetChain() {
        chain = new ArrayList<ChainEntry>();
        chainState = ChainState.INIT;
    }

    /**
     * Load rules from a Reader stream.
     *
     * @return success or failure
     */
    public boolean parseRules(java.io.Reader rulesStream) {
        chainState = ChainState.PARTIAL;

        // Now read in the rules.txt file
        try {
            BufferedReader input = new BufferedReader(rulesStream);
            String line;
            Rule currentRule = null;
            Integer count = 0;
            Integer lineNo = 0;

            while ((line = input.readLine()) != null) {
                lineNo++;
                line = line.trim();
                String command;
                String lineData = "";

                // SKIP this line if it is empty, or a comment
                if (line.isEmpty() || line.matches("^#.*")) continue;

                // SPLIT the line into the token and remainder.
                {
                    String[] parts = line.split("\\s", 2);
                    command = parts[0];
                    if (parts.length > 1 ) {
                        lineData = parts[1];
                    }
                }

                // Statements which will terminate a rule
                // TODO: Rewrite the parser logic.  Should a blank line terminate a rule, instead?

                if (command.matches("match|catch|replace|rewrite|include")) {
                    // This terminates the last rule.
                    // If we currently have a valid rule, add it to the set.
                    if (currentRule != null && currentRule.isValid()) {
                        append(currentRule);
                        count++;
                    }

                    if (command.equalsIgnoreCase("include")) {
                        // We need to find and parse the dependant file.  It may be valid
                        // currently, but we are going to force it to reload, to ensure
                        // no recursion loops.
                        LogManager.getInstance().debugLogMedium("Including chain: " + lineData + " in: " + getConfigName());
                        RuleChain includedChain = manager.getRuleChain(lineData);
                        if (includedChain.chainState == ChainState.PARTIAL) {
                            LogManager.logger.warning("Recursion loop detected in: " + getConfigName() +
                            "at line #:" + lineNo);
                            continue;
                        }
                        if (includedChain.loadConfigFile()) {
                            append(includedChain);
                            count = count + includedChain.ruleCount();
                        } else {
                            LogManager.logger.warning("Failed to include: " + lineData + " in rulechain: " + configName + ".");
                        }
                    } else {
                        // Now start on a new rule.  If the match string is invalid, we'll still get the new rule,
                        // and we'll still collect statements until the next match, but we'll throw it all away,
                        // because it won't be valid.
                        currentRule = new Rule(lineData);
                    }

                } else {
                    // Not a match statement, so much be part of a rule.
                    if (currentRule != null) {
                        if (!currentRule.addLine(command, lineData)) {
                            LogManager.logger.warning("Unable to add action/condition to rule: " + command + " " + lineData);
                        }
                    }
                }
            }

            // Make sure we add the last action
            if (currentRule != null && currentRule.isValid()) append(currentRule);

            input.close();

            LogManager.logger.config("Read " + count.toString() + " rules from file: " + getConfigName() + ".");
            chainState = ChainState.READY;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return !chain.isEmpty();
    }

}