package privateadmin.internal
enum class UiLanguage { EN, HI, PA }
fun uiText(en:String,hi:String,pa:String,lang:UiLanguage)=when(lang){UiLanguage.EN->en;UiLanguage.HI->hi;UiLanguage.PA->pa}
