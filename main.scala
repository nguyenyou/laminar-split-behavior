//> using scala 3.7.3
//> using platform scala-js
//> using dep "org.scala-js::scalajs-dom::2.8.1"
//> using dep "com.raquo::laminar::17.2.1"
//> using jsModuleKind es

import org.scalajs.dom
import com.raquo.laminar.api.L.*

var trueCount = 0
var falseCount = 0

@main
def run(): Unit = {
  val container = dom.document.getElementById("app")
  val isOn = Var(false)

  val app = div(
    h2("splitBoolean re-render test"),
    button(
      "Toggle",
      onClick --> { _ => isOn.update(!_) }
    ),
    p("Current value: ", child.text <-- isOn.signal.map(_.toString)),
    child <-- isOn.signal.splitBoolean(
      whenTrue = { _ =>
        trueCount += 1
        dom.console.log(s"whenTrue called (count: $trueCount)")
        div(
          p(s"TRUE branch rendered (call #$trueCount)"),
          p(
            styleAttr := "color: green; font-weight: bold",
            s"whenTrue was called $trueCount time(s)"
          )
        )
      },
      whenFalse = { _ =>
        falseCount += 1
        dom.console.log(s"whenFalse called (count: $falseCount)")
        div(
          p(s"FALSE branch rendered (call #$falseCount)"),
          p(
            styleAttr := "color: red; font-weight: bold",
            s"whenFalse was called $falseCount time(s)"
          )
        )
      }
    )
  )

  render(container, app)
}
