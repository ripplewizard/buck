/*
 * Copyright 2017-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.rules.keys;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.event.BuckEventBus;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.model.BuildId;
import com.facebook.buck.rules.ActionGraph;
import com.facebook.buck.rules.RuleKeyAppendable;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.facebook.buck.testutil.WatchEventsForTests;
import com.facebook.buck.timing.FakeClock;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.eventbus.EventBus;

import org.junit.Test;

import java.nio.file.StandardWatchEventKinds;

public class RuleKeyCacheRecyclerTest {

  private static final ProjectFilesystem FILESYSTEM = new FakeProjectFilesystem();
  private static final EventBus EVENT_BUS = new EventBus();
  private static final BuckEventBus BUCK_EVENT_BUS =
      new BuckEventBus(new FakeClock(0), new BuildId());
  private static final int RULE_KEY_SEED = 0;
  private static final ActionGraph ACTION_GRAPH = new ActionGraph(ImmutableList.of());
  private static final RuleKeyCacheRecycler.SettingsAffectingCache SETTINGS =
      new RuleKeyCacheRecycler.SettingsAffectingCache(RULE_KEY_SEED, ACTION_GRAPH);

  @Test
  public void pathWatchEventDoesNotInvalidateDifferentInput() {
    DefaultRuleKeyCache<Void> cache = new DefaultRuleKeyCache<>();
    RuleKeyInput input1 = RuleKeyInput.of(FILESYSTEM, FILESYSTEM.getPath("input1"));
    RuleKeyAppendable appendable1 = sink -> {};
    RuleKeyInput input2 = RuleKeyInput.of(FILESYSTEM, FILESYSTEM.getPath("input2"));
    RuleKeyAppendable appendable2 = sink -> {};
    cache.get(
        appendable1,
        a -> new RuleKeyResult<>(null, ImmutableList.of(), ImmutableList.of(input1)));
    cache.get(
        appendable2,
        a -> new RuleKeyResult<>(null, ImmutableList.of(), ImmutableList.of(input2)));
    RuleKeyCacheRecycler<Void> recycler =
        RuleKeyCacheRecycler.createAndRegister(EVENT_BUS, cache, ImmutableSet.of(FILESYSTEM));
    recycler.onFilesystemChange(
        WatchEventsForTests.createPathEvent(
            input2.getPath(),
            StandardWatchEventKinds.ENTRY_MODIFY));
    assertTrue(cache.isCached(appendable1));
    assertFalse(cache.isCached(appendable2));
  }

  @Test
  public void overflowWatchEventInvalidatesEverything() {
    DefaultRuleKeyCache<Void> cache = new DefaultRuleKeyCache<>();

    // Create a rule key appendable with an input and cache it.
    RuleKeyInput input1 = RuleKeyInput.of(FILESYSTEM, FILESYSTEM.getPath("input1"));
    RuleKeyAppendable appendable1 = sink -> {};
    cache.get(
        appendable1,
        a -> new RuleKeyResult<>(null, ImmutableList.of(), ImmutableList.of(input1)));

    // Create another rule key appendable with an input and cache it.
    RuleKeyInput input2 = RuleKeyInput.of(FILESYSTEM, FILESYSTEM.getPath("input2"));
    RuleKeyAppendable appendable2 = sink -> {};
    cache.get(
        appendable2,
        a -> new RuleKeyResult<>(null, ImmutableList.of(), ImmutableList.of(input2)));

    RuleKeyCacheRecycler<Void> recycler =
        RuleKeyCacheRecycler.createAndRegister(EVENT_BUS, cache, ImmutableSet.of(FILESYSTEM));

    // Verify that everything is cached before the overflow event.
    assertTrue(cache.isCached(appendable1));
    assertTrue(cache.isCached(appendable2));

    // Send an overflow event and verify everything was invalidated.
    recycler.onFilesystemChange(WatchEventsForTests.createOverflowEvent());
    assertFalse(cache.isCached(appendable1));
    assertFalse(cache.isCached(appendable2));
  }

  @Test
  public void getCacheWithIdenticalSettingsDoesNotInvalidate() {
    DefaultRuleKeyCache<Void> cache = new DefaultRuleKeyCache<>();
    RuleKeyCacheRecycler<Void> recycler =
        RuleKeyCacheRecycler.createAndRegister(EVENT_BUS, cache, ImmutableSet.of(FILESYSTEM));
    RuleKeyAppendable appendable = sink -> {};
    recycler.withRecycledCache(
        BUCK_EVENT_BUS,
        SETTINGS,
        c -> {
          cache.get(
              appendable,
              a -> new RuleKeyResult<>(null, ImmutableList.of(), ImmutableList.of()));
        });
    assertTrue(cache.isCached(appendable));
    recycler.withRecycledCache(
        BUCK_EVENT_BUS,
        SETTINGS,
        c -> {});
    assertTrue(cache.isCached(appendable));
  }

  @Test
  public void getCacheWithDifferentRuleKeySeedInvalidates() {
    DefaultRuleKeyCache<Void> cache = new DefaultRuleKeyCache<>();
    RuleKeyCacheRecycler<Void> recycler =
        RuleKeyCacheRecycler.createAndRegister(EVENT_BUS, cache, ImmutableSet.of(FILESYSTEM));
    RuleKeyAppendable appendable = sink -> {};
    recycler.withRecycledCache(
        BUCK_EVENT_BUS,
        SETTINGS,
        c -> {
          cache.get(
              appendable,
              a -> new RuleKeyResult<>(null, ImmutableList.of(), ImmutableList.of()));
        });
    assertTrue(cache.isCached(appendable));
    recycler.withRecycledCache(
        BUCK_EVENT_BUS,
        new RuleKeyCacheRecycler.SettingsAffectingCache(RULE_KEY_SEED + 1, ACTION_GRAPH),
        c -> {});
    assertFalse(cache.isCached(appendable));
  }

  @Test
  public void getCacheWithDifferentActionGraphInstanceInvalidates() {
    DefaultRuleKeyCache<Void> cache = new DefaultRuleKeyCache<>();
    RuleKeyCacheRecycler<Void> recycler =
        RuleKeyCacheRecycler.createAndRegister(EVENT_BUS, cache, ImmutableSet.of(FILESYSTEM));
    RuleKeyAppendable appendable = sink -> {};
    recycler.withRecycledCache(
        BUCK_EVENT_BUS,
        SETTINGS,
        c -> {
          cache.get(
              appendable,
              a -> new RuleKeyResult<>(null, ImmutableList.of(), ImmutableList.of()));
        });
    assertTrue(cache.isCached(appendable));
    recycler.withRecycledCache(
        BUCK_EVENT_BUS,
        new RuleKeyCacheRecycler.SettingsAffectingCache(
            RULE_KEY_SEED,
            new ActionGraph(ImmutableList.of())),
        c -> {});
    assertFalse(cache.isCached(appendable));
  }

}