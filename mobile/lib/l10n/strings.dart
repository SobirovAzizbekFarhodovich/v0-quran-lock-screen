class Strings {
  Strings(this.language);

  final String language;

  static const supported = ['en', 'ru', 'uz', 'uzk'];

  static const _values = {
    'en': {
      'title': 'Islamic AI',
      'login': 'Login',
      'register': 'Register',
      'email': 'Email',
      'password': 'Password',
      'name': 'Name',
      'spiritual': 'Spiritual',
      'recitation': 'Recitation',
      'profile': 'Profile',
      'sessions': 'Sessions',
      'ask': 'Describe your state',
      'send': 'Send',
      'start': 'Start',
      'stop': 'Stop',
    },
    'ru': {
      'title': 'Islamic AI',
      'login': 'Войти',
      'register': 'Регистрация',
      'email': 'Email',
      'password': 'Пароль',
      'name': 'Имя',
      'spiritual': 'Поддержка',
      'recitation': 'Чтение',
      'profile': 'Профиль',
      'sessions': 'История',
      'ask': 'Опишите состояние',
      'send': 'Отправить',
      'start': 'Старт',
      'stop': 'Стоп',
    },
    'uz': {
      'title': 'Islamic AI',
      'login': 'Kirish',
      'register': 'Royxatdan otish',
      'email': 'Email',
      'password': 'Parol',
      'name': 'Ism',
      'spiritual': 'Ruhiy taskin',
      'recitation': 'Qiroat',
      'profile': 'Profil',
      'sessions': 'Tarix',
      'ask': 'Holatingizni yozing',
      'send': 'Yuborish',
      'start': 'Boshlash',
      'stop': 'Toxtatish',
    },
    'uzk': {
      'title': 'Islamic AI',
      'login': 'Кириш',
      'register': 'Руйхатдан утиш',
      'email': 'Email',
      'password': 'Парол',
      'name': 'Исм',
      'spiritual': 'Руҳий таскин',
      'recitation': 'Қироат',
      'profile': 'Профил',
      'sessions': 'Тарих',
      'ask': 'Ҳолатингизни ёзинг',
      'send': 'Юбориш',
      'start': 'Бошлаш',
      'stop': 'Тухтатиш',
    },
  };

  String t(String key) => _values[language]?[key] ?? _values['en']![key] ?? key;
}
