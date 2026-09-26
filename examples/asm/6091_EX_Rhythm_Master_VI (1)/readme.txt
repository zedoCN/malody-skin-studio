关于imd谱面读取方式
皮肤支持imd解析，但谱面需要mc占位，下面链接下载的谱包解压后可直接游玩
【【节奏大师停服】 谱面归档计划 已达成-哔哩哔哩】 https://b23.tv/PZYFWt8
自制谱游玩教程:
关于自制谱，需要mc文件占位，并且有对应的imd文件，imd文件命名方式以title的第一个下划线前的内容和version难度名为基准。如果title没有下划线，则以整个title为基准，格式如下:
mc的title:ABC_xyz或ABC
imd对应的文件名为:ABC_{mode}_{version}.imd
mode包括4k，5k，6k
version包括ez,nm,hd,mx,sp
比如自制谱谱包的imd名字是"xiaopingguo_4k_hd.imd"
则对应的mc的title应该是"xiaopingguo_任意内容"或者"xiaopingguo"，version应该是rm_4k_hd
新增json解析，命名规则同上