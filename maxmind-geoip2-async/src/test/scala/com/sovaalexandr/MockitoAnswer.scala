package com.sovaalexandr

import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer

trait MockitoAnswer {

  def answer[T](f: InvocationOnMock => T): Answer[T] = (invocation: InvocationOnMock) => f(invocation)
}
