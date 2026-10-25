-- Account numbers are random: the constraint rejects a collision and the wallet is created again with a new number.
-- The name is referenced by WalletServiceImp to tell this violation apart from the others.
alter table wallet add constraint uk_wallet_account_number unique (account_number);
