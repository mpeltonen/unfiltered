package unfiltered.filter.async

import org.eclipse.jetty.continuation.ContinuationSupport
import org.eclipse.jetty.continuation.Continuation
import util._
import unfiltered.filter.{AsyncBinding,RequestBinding,ResponseBinding}
import unfiltered.response.{NotFound, Pass}
import unfiltered.request.HttpRequest
import javax.servlet.{Filter, FilterConfig, FilterChain, ServletRequest, ServletResponse}
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import unfiltered.Async

object Plan {
 
  type Intent =
    Async.Intent[HttpServletRequest,HttpServletResponse]
}

/** Object to facilitate Plan.Intent definitions. Type annotations
 *  are another option. */
object Intent {
  def apply(intent: Async.PF[HttpServletRequest,HttpServletResponse]) =
    unfiltered.Async.Intent(intent)
}

/**
* provides an asynchronous Plan that's using jetty continuation under the hood
* the implementation follows the so called "Suspend Continue Pattern" where
* an asynchronous handler is used to generate the response
*/
trait Plan extends unfiltered.filter.InittedFilter {
  
  def intent: Plan.Intent

  def doFilter(request: ServletRequest,
                        response: ServletResponse,
                        chain: FilterChain) {
    val continuation = ContinuationSupport.getContinuation(request)
    if (continuation.isExpired) {
       response.asInstanceOf[HttpServletResponse].setStatus(408)
       chain.doFilter(request,response)
    } else {
      continuation.suspend()
      (request, response) match {
        case (hreq: HttpServletRequest, hres: HttpServletResponse) =>
          intent.fold(
            (_)   => chain.doFilter(hreq, hres),
            (_,_) => ()
          )(new RequestBinding(hreq) with AsyncBinding {
             val con = continuation
          })
      }
    }
  }
}

class Planify(val intent: Plan.Intent) extends Plan

object Planify {
  def apply(intent: Plan.Intent) = new Planify(intent)
}