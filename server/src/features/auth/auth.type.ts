export type IPublicUser = {
  id: string;
  name: string;
  email: string;
  createdAt: Date;
};

export type AuthResult = {
  token: string;
  user: IPublicUser;
};

export type IServiceRegisterParams = {
  name: string;
  email: string;
  password: string;
};

export type IServiceLoginParams = {
  email: string;
  password: string;
};

export type UserRequest = {
  user: IPublicUser;
};
