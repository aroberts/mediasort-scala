package mediasort.config

case class Config(
    logPath: String,
    omdbApiKey: String,
    actions: List[Trigger]
) {

}
