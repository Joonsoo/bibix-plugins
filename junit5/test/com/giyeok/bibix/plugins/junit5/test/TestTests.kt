package com.giyeok.bibix.plugins.junit5.test

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TestTests {
  @Test
  fun test1() {
    assertThat("123").isEqualTo("123")
  }

  @Test
  fun test2() {
    assertThat("123").isEqualTo("123")
  }
}
