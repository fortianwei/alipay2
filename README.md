alipay2
=======

针对支付宝官方alipay.jar优化一些逻辑之后的sdk

##修改官方sdk的原因：

- 官方sdk接入时，在一些低内存手机上容易出现自己的*app被杀，闪退*的现象
- 有时候并不想弹出那个提示是否安装或者下载的按钮，直接跳转网页支付


##先解释下为什么会被杀掉：

某些时刻，当手机处于内存爆满的时候，弹出msp/client，会导致自己的app被系统杀掉
这个的原因，是因为我们的app和弹出的msp/client`不在一个进程`，这个时候，`自己的app处于挂起状态`
（因为弹出框不是全屏的，app是半透明状态），`挂起的app是可能被系统杀掉的`(原因就是系统内存不足的时候，先杀后台进程，再杀挂起进程）。而如果使用网页支付，这个activity
是在我们的AndroidManifest.xml中注册并调用的，所以是在本进程之内，所以我们app进程不会被杀掉。

注意，上面说的msp/client含义是：

`msp = 快捷支付`

`client = 支付宝钱包支付`

##如何使用？

拷贝`dist`下面的alipay.jar替换掉你原来使用的那个alipay.jar即可。

在调用的时候加上你自己设定的内存滴的阈值，默认是300，意思是低于300M的话就算是低内存状态，这个时候就会自动优先使用网页计费而不是msp或者client计费。

例如：AliPay.setMemoryThreadshold(200); 就是设定低于200M的时候算是低内存状态。

如果你是根据官方demo接入的话，那么就类似下面：
<pre>
					AliPay alipay = new AliPay(ExternalPartner.this, mHandler);
					
					//设置为沙箱模式，不设置默认为线上环境
//					alipay.setSandBox(true);
					AliPay.setMemoryThreadshold(200);//添加这句话，不添加默认设定为300M是低内存状态
					String result = alipay.pay(orderInfo);
</pre>

##新增alipay_lib

如果down下alipay2工程，会依赖这个工程。这个原本是官方sdk中带着的，为了方便使用，也把它放进去。需要把它单独提取出来作为android library 供alipay2使用。

##如何修改代码生成自己的alipay.jar？

可以拷贝本项目 git clone https://github.com/fortianwei/alipay2   到本地，将里面的alipay_lib单独拿出来作为一个lib工程，然后就可以修改里面的代码了。

将alipay2/lib/下的alipay.jar(这个是官方原版的）解压到目录alipay，然后用我们自己编译好的class文件(eclipse的话一般在bin/目录下)替换掉原有的class文件。

执行命令 jar cvf alipay.jar  alipay   就生成自己版本的jar包了，直接替换原有工程里面的jar包即可。


##发现问题/有其他建议？

可以fork && pull request，或者加我qq 524838881，alipay计费一直有很多朋友不明确，我也希望做好点以后方便大家新手朋友们使用。

