/*
 * Copyright 2013 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.textsecuregcm.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import org.junit.jupiter.api.Test;
import org.whispersystems.textsecuregcm.configuration.CircuitBreakerConfiguration;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisException;

class ReplicatedJedisPoolTest {

  @Test
  void testWriteCheckoutNoSlaves() {
    JedisPool master = mock(JedisPool.class);

    try {
      new ReplicatedJedisPool("testWriteCheckoutNoSlaves", master, new LinkedList<>(), new CircuitBreakerConfiguration());
      throw new AssertionError();
    } catch (Exception e) {
      // good
    }
  }

  @Test
  void testWriteCheckoutWithSlaves() {
    JedisPool master   = mock(JedisPool.class);
    JedisPool slave    = mock(JedisPool.class);
    Jedis     instance = mock(Jedis.class    );

    when(master.getResource()).thenReturn(instance);

    ReplicatedJedisPool replicatedJedisPool = new ReplicatedJedisPool("testWriteCheckoutWithSlaves", master, Collections.singletonList(slave), new CircuitBreakerConfiguration());
    Jedis writeResource = replicatedJedisPool.getWriteResource();

    assertThat(writeResource).isEqualTo(instance);
    verify(master, times(1)).getResource();
  }

  @Test
  void testReadCheckouts() {
    JedisPool master      = mock(JedisPool.class);
    JedisPool slaveOne    = mock(JedisPool.class);
    JedisPool slaveTwo    = mock(JedisPool.class);
    Jedis     instanceOne = mock(Jedis.class    );
    Jedis     instanceTwo = mock(Jedis.class    );

    when(slaveOne.getResource()).thenReturn(instanceOne);
    when(slaveTwo.getResource()).thenReturn(instanceTwo);

    ReplicatedJedisPool replicatedJedisPool = new ReplicatedJedisPool("testReadCheckouts", master, Arrays.asList(slaveOne, slaveTwo), new CircuitBreakerConfiguration());

    assertThat(replicatedJedisPool.getReadResource()).isEqualTo(instanceOne);
    assertThat(replicatedJedisPool.getReadResource()).isEqualTo(instanceTwo);
    assertThat(replicatedJedisPool.getReadResource()).isEqualTo(instanceOne);
    assertThat(replicatedJedisPool.getReadResource()).isEqualTo(instanceTwo);
    assertThat(replicatedJedisPool.getReadResource()).isEqualTo(instanceOne);

    verifyNoMoreInteractions(master);
  }

  @Test
  void testBrokenReadCheckout() {
    JedisPool master      = mock(JedisPool.class);
    JedisPool slaveOne    = mock(JedisPool.class);
    JedisPool slaveTwo    = mock(JedisPool.class);
    Jedis     instanceTwo = mock(Jedis.class    );

    when(slaveOne.getResource()).thenThrow(new JedisException("Connection failed!"));
    when(slaveTwo.getResource()).thenReturn(instanceTwo);

    ReplicatedJedisPool replicatedJedisPool = new ReplicatedJedisPool("testBrokenReadCheckout", master, Arrays.asList(slaveOne, slaveTwo), new CircuitBreakerConfiguration());

    assertThat(replicatedJedisPool.getReadResource()).isEqualTo(instanceTwo);
    assertThat(replicatedJedisPool.getReadResource()).isEqualTo(instanceTwo);
    assertThat(replicatedJedisPool.getReadResource()).isEqualTo(instanceTwo);

    verifyNoMoreInteractions(master);
  }

  @Test
  void testAllBrokenReadCheckout() {
    JedisPool master      = mock(JedisPool.class);
    JedisPool slaveOne    = mock(JedisPool.class);
    JedisPool slaveTwo    = mock(JedisPool.class);

    when(slaveOne.getResource()).thenThrow(new JedisException("Connection failed!"));
    when(slaveTwo.getResource()).thenThrow(new JedisException("Also failed!"));

    ReplicatedJedisPool replicatedJedisPool = new ReplicatedJedisPool("testAllBrokenReadCheckout", master, Arrays.asList(slaveOne, slaveTwo), new CircuitBreakerConfiguration());

    try {
      replicatedJedisPool.getReadResource();
      throw new AssertionError();
    } catch (Exception e) {
      // good
    }

    verifyNoMoreInteractions(master);
  }

  @Test
  void testCircuitBreakerOpen() {
    CircuitBreakerConfiguration configuration = new CircuitBreakerConfiguration();
    configuration.setFailureRateThreshold(50);
    configuration.setSlidingWindowSize(2);
    configuration.setSlidingWindowMinimumNumberOfCalls(2);

    JedisPool master = mock(JedisPool.class);
    JedisPool slaveOne = mock(JedisPool.class);
    JedisPool slaveTwo = mock(JedisPool.class);

    when(master.getResource()).thenReturn(null);
    when(slaveOne.getResource()).thenThrow(new JedisException("Connection failed!"));
    when(slaveTwo.getResource()).thenThrow(new JedisException("Also failed!"));

    ReplicatedJedisPool replicatedJedisPool = new ReplicatedJedisPool("testCircuitBreakerOpen", master,
        Arrays.asList(slaveOne, slaveTwo), configuration);
    replicatedJedisPool.getWriteResource();

    when(master.getResource()).thenThrow(new JedisException("Master broken!"));

    try {
      replicatedJedisPool.getWriteResource();
      throw new AssertionError();
    } catch (JedisException exception) {
      // good
    }

    try {
      replicatedJedisPool.getWriteResource();
      throw new AssertionError();
    } catch (CallNotPermittedException e) {
      // good
    }
  }

  @Test
  void testCircuitBreakerHalfOpen() throws InterruptedException {
    CircuitBreakerConfiguration configuration = new CircuitBreakerConfiguration();
    configuration.setFailureRateThreshold(50);
    configuration.setSlidingWindowSize(2);
    configuration.setSlidingWindowMinimumNumberOfCalls(2);
    configuration.setPermittedNumberOfCallsInHalfOpenState(1);
    configuration.setWaitDurationInOpenStateInSeconds(1);

    JedisPool master = mock(JedisPool.class);
    JedisPool slaveOne = mock(JedisPool.class);
    JedisPool slaveTwo = mock(JedisPool.class);

    when(master.getResource()).thenThrow(new JedisException("Master broken!"));
    when(slaveOne.getResource()).thenThrow(new JedisException("Connection failed!"));
    when(slaveTwo.getResource()).thenThrow(new JedisException("Also failed!"));

    ReplicatedJedisPool replicatedJedisPool = new ReplicatedJedisPool("testCircuitBreakerHalfOpen", master, Arrays.asList(slaveOne, slaveTwo), configuration);

    try {
      replicatedJedisPool.getWriteResource();
      throw new AssertionError();
    } catch (JedisException exception) {
      // good
    }

    try {
      replicatedJedisPool.getWriteResource();
      throw new AssertionError();
    } catch (JedisException exception) {
      // good
    }

    try {
      replicatedJedisPool.getWriteResource();
      throw new AssertionError();
    } catch (CallNotPermittedException e) {
      // good
    }

    Thread.sleep(1100);

    try {
      replicatedJedisPool.getWriteResource();
      throw new AssertionError();
    } catch (JedisException exception) {
      // good
    }

    try {
      replicatedJedisPool.getWriteResource();
      throw new AssertionError();
    } catch (CallNotPermittedException e) {
      // good
    }
  }

}
