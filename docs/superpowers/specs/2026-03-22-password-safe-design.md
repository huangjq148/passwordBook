# PasswordSafe 密码管理应用设计文档

## 一、应用流程

```
启动应用
    ↓
首次使用？
    ├─ 是 → 引导设置主密码页面 → 设置成功 → 进入主页面
    └─ 否 → 显示验证页面 → 验证通过 → 进入主页面
```

**首次设置主密码页面**
- 设置主密码输入框（需二次确认）
- 密码强度指示器
- 可选：启用生物识别验证开关
- 跳过按钮（不设置验证，显示安全警告）

**主页面结构**（底部双 Tab）：

**Tab 1：首页**
- 顶部安全状态指示器
- 顶部搜索框
- 最近使用的 5 个账号（按最后使用时间排序）
- 添加按钮（悬浮按钮）
- 点击搜索 → 跳转到完整账号列表页面并激活搜索

**Tab 2：我的**
- 安全状态卡片
  - 当前安全等级
  - 设置建议
- 设置密码验证开关
- 设置生物识别验证开关（指纹/人脸）
- 修改密码入口
- 自动锁定时间设置
- 导出为 Excel
- 从 Excel 导入
- 复制为文本
- 重置应用（清空所有数据）

**账号列表页面**（从首页搜索进入）
- 搜索框（带过滤）
- 完整账号列表（按使用频率排序）
- 点击项 → 账号详情页面

**账号详情页面**
- 显示完整账号信息
- 编辑按钮 → 编辑页面
- 删除按钮
- 复制账号/密码按钮

**添加/编辑页面**
- 网站名称（必填）
- 网站 URL（选填）
- 账号（必填）
- 密码（必填）
- 密码生成按钮 → 弹出生成器对话框
- 密码强度指示器
- 备注（选填）
- 分类标签（选填）

**密码生成器对话框**
- 密码长度滑块（8-32位，默认16位）
- 字符类型选项：
  - 大写字母（A-Z）
  - 小写字母（a-z）
  - 数字（0-9）
  - 特殊符号（!@#$%^&*）
- 生成的密码预览
- 刷新按钮（重新生成）
- 复制按钮
- 使用按钮（填入密码字段）

---

## 二、用户体验优化

### 1. 自动填充服务

集成 Android Autofill Service，当用户在其他应用登录时：
- 自动检测登录页面
- 弹出悬浮窗/弹窗显示匹配的账号
- 用户选择后自动填充账号密码
- 需要用户授权并在系统设置中启用

**配置要求**：

`res/xml/autofillservice.xml`:
```xml
<autofill-service xmlns:android="http://schemas.android.com/apk/res/android">
    <compatibility-package
        android:name="*"
        android:maxLongVersionCode="10000000000" />
</autofill-service>
```

`AndroidManifest.xml` 中添加：
```xml
<service
    android:name=".ui.autofill.AutofillService"
    android:label="@string/app_name"
    android:permission="android.permission.BIND_AUTOFILL_SERVICE">
    <intent-filter>
        <action android:name="android.autofill.AutofillService" />
    </intent-filter>
    <meta-data
        android:name="android.autofill"
        android:resource="@xml/autofillservice" />
</service>
```

### 2. 情感化安全反馈

- 首页顶部显示安全状态指示器
  - 绿色盾牌 + "安全" → 已设置验证方式
  - 橙色盾牌 + "建议设置验证" → 未设置验证
- 添加密码时检测密码强度，给予反馈
- 密码强度条 + 文字提示（弱/中/强）

---

## 三、安全机制

### 1. 启动验证
- 首次使用需引导设置主密码
- 已设置验证后，启动时需生物识别或密码验证
- 用户可选择跳过设置（显示安全警告）

### 2. 验证方式

**生物识别**
- 指纹识别（设备支持时）
- 人脸识别（设备支持时）
- 系统自动选择设备支持的生物识别方式

**密码验证**
- 作为生物识别的备用方式
- 用户可在设置中启用/禁用
- 生物识别失败时可切换到密码验证

### 3. 自动锁定
- 应用切入后台超过设定时间自动上锁
- 默认锁定时间：1 分钟
- 可选时间：立即/1分钟/5分钟/15分钟
- 从后台返回时需重新验证
- 待机后返回也需验证

### 4. 忘记密码处理

**安全策略**：
- 主密码用于派生加密密钥，忘记后无法恢复数据
- 处理方式：
  1. 显示提示：输入错误密码 5 次后锁定
  2. 提供"重置应用"选项
  3. 重置将清空所有账号数据，恢复到初始状态
- 用户设置密码时显示警告提示

---

## 四、数据模型

### 1. 账号信息表（Account）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键，自增 |
| website | String | 网站名称 |
| websiteUrl | String | 网站 URL |
| account | String | 账号 |
| password | String | 密码（加密存储） |
| notes | String | 备注 |
| category | String | 分类标签 |
| useCount | Int | 使用次数（用于频率排序） |
| lastUsedAt | Long | 最后使用时间戳 |
| createdAt | Long | 创建时间戳 |
| updatedAt | Long | 更新时间戳 |

### 2. 应用设置表（AppSetting）

| 字段 | 类型 | 说明 |
|------|------|------|
| key | String | 设置项键名 |
| value | String | 设置项值 |

**设置项**：
- `is_first_time` - 是否首次使用
- `biometric_enabled` - 生物识别验证开关
- `password_enabled` - 密码验证开关
- `app_password` - 应用密码（加密存储）
- `auto_lock_timeout` - 自动锁定时间（毫秒）
- `search_history` - 搜索历史（JSON 数组格式）

---

## 五、搜索功能

### 智能搜索特性

1. **模糊匹配**
   - 搜索范围：网站名称、账号、备注、分类标签
   - 支持部分匹配，如 "goo" 匹配 "Google"
   - 不区分大小写

2. **排序规则**
   - 默认按使用频率排序（点击次数多的排前面）
   - 相同频率按最近使用时间排序
   - 搜索结果同样按频率排序

3. **搜索入口**
   - 首页搜索框 → 点击跳转完整列表页并激活搜索
   - 账号列表页顶部搜索框

4. **搜索历史**
   - 存储在 AppSetting 表的 `search_history` 字段（JSON 数组）
   - 记录最近 5 个搜索词
   - 搜索框获焦时显示历史记录
   - 支持清空历史记录

---

## 六、模块架构

```
com.example.passwordsafe/
├── PasswordSafeApplication.kt    # Application 类
├── MainActivity.kt               # 主 Activity
│
├── data/                         # 数据层
│   ├── model/                    # 数据模型
│   │   ├── Account.kt            # 账号实体
│   │   └── AppSetting.kt         # 设置实体
│   ├── local/                    # 本地存储
│   │   ├── database/
│   │   │   ├── AppDatabase.kt    # Room 数据库
│   │   │   ├── AccountDao.kt     # 账号 DAO
│   │   │   └── SettingDao.kt     # 设置 DAO
│   │   └── preferences/
│   │       └── AppPreferences.kt # SharedPreferences
│   ├── crypto/                   # 加密模块
│   │   └── CryptoManager.kt      # Keystore + AES 加密
│   └── repository/               # 数据仓库
│       ├── AccountRepository.kt
│       └── SettingRepository.kt
│
├── di/                           # 依赖注入
│   └── AppModule.kt              # Hilt 模块
│
├── ui/                           # UI 层
│   ├── auth/                     # 验证模块
│   │   ├── AuthFragment.kt
│   │   ├── SetupPasswordFragment.kt
│   │   └── AuthViewModel.kt
│   ├── home/                     # 首页模块
│   │   ├── HomeFragment.kt
│   │   └── HomeViewModel.kt
│   ├── accounts/                 # 账号列表模块
│   │   ├── AccountsFragment.kt
│   │   └── AccountsViewModel.kt
│   ├── detail/                   # 账号详情模块
│   │   ├── DetailFragment.kt
│   │   └── DetailViewModel.kt
│   ├── addedit/                  # 添加/编辑模块
│   │   ├── AddEditFragment.kt
│   │   ├── AddEditViewModel.kt
│   │   └── PasswordGeneratorDialog.kt
│   ├── settings/                 # 设置模块
│   │   ├── SettingsFragment.kt
│   │   └── SettingsViewModel.kt
│   └── autofill/                 # 自动填充服务
│       └── AutofillService.kt
│
└── util/                         # 工具类
    ├── BiometricHelper.kt        # 生物识别工具
    ├── AutoLockManager.kt        # 自动锁定管理
    ├── PasswordStrengthChecker.kt # 密码强度检测
    └── PasswordGenerator.kt      # 密码生成器
```

---

## 七、导入导出功能

### 1. 导出为 Excel
- 使用 Apache POI 库生成 .xlsx 文件
- 导出字段：网站、URL、账号、密码、备注、分类、创建时间
- 保存路径：应用私有目录或用户选择的目录
- 导出完成后提示分享/保存位置

**技术方案说明**：
- Apache POI 库体积较大（约 10MB+），会增加 APK 体积
- 优点：功能完整，支持标准 xlsx 格式
- 替代方案：如果 APK 体积敏感，可考虑 CSV 格式（无需额外依赖）

### 2. 导入 Excel
- 支持导入 .xlsx 文件
- 文件格式需与导出格式一致
- 导入逻辑：
  - 读取文件中所有账号数据
  - 按"网站+账号"作为唯一标识
  - 已存在的账号：提示用户选择跳过或覆盖
  - 新账号：直接添加
- 导入完成后显示导入统计（新增 X 条，跳过 X 条，覆盖 X 条）

### 3. 复制为文本
- 复制所有账号信息到剪贴板
- 格式：
  ```
  网站: Google
  URL: https://google.com
  账号: user@example.com
  密码: ********
  备注: 主账号
  分类: 工作
  -------------------
  网站: GitHub
  ...
  ```
- 复制后显示 Toast 提示

### 4. 安全提醒
- 导出/复制/导入前显示警告弹窗：操作涉及明文密码，请注意安全
- 用户确认后才执行操作

---

## 八、技术依赖

### 核心依赖

```gradle
// Room 数据库
implementation 'androidx.room:room-runtime:2.4.3'
implementation 'androidx.room:room-ktx:2.4.3'
kapt 'androidx.room:room-compiler:2.4.3'

// 生物识别
implementation 'androidx.biometric:biometric:1.1.0'

// Excel 处理（可选，如需 CSV 可移除）
implementation 'org.apache.poi:poi:5.2.3'
implementation 'org.apache.poi:poi-ooxml:5.2.3'

// 安全加密
implementation 'androidx.security:security-crypto:1.1.0-alpha06'

// 依赖注入
implementation 'com.google.dagger:hilt-android:2.44'
kapt 'com.google.dagger:hilt-compiler:2.44'

// Lifecycle & Navigation（已有）
implementation 'androidx.lifecycle:lifecycle-livedata-ktx:2.4.1'
implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.4.1'
implementation 'androidx.navigation:navigation-fragment-ktx:2.4.1'
implementation 'androidx.navigation:navigation-ui-ktx:2.4.1'
```

### 需要添加的权限

```xml
<!-- 生物识别 -->
<uses-permission android:name="android.permission.USE_BIOMETRIC" />

<!-- 自动填充服务（可选） -->
<uses-permission android:name="android.permission.BIND_AUTOFILL_SERVICE" />
```

---

## 九、技术方案

### 数据存储
- **方式**：本地 Room 数据库
- **安全性**：Android Keystore + AES 加密

### 加密方案
- 使用 Android Keystore 存储加密密钥
- 密钥存储在硬件安全模块中
- 使用 AES-GCM 算法加密密码字段

### 架构方案
- **方案**：单 Activity + 多 Fragment
- **优势**：复用现有 Navigation 组件，改动最小
- **导航**：使用 Navigation 组件管理 Fragment 切换

### 依赖注入
- **框架**：Hilt
- **优势**：Google 官方推荐，与 Android 集成良好
- **用途**：管理 ViewModel、Repository 等组件的依赖