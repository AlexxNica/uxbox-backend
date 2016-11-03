-- :name get-image-collection :? :1
select *
  from images_collections as cc
 where cc.id = :id
   and cc."user" = '00000000-0000-0000-0000-000000000000'::uuid;

-- :name create-image :<! :1
insert into images ("user", name, collection, path, width, height, mimetype)
values ('00000000-0000-0000-0000-000000000000'::uuid, :name, :collection,
        :path, :width, :height, :mimetype)
returning *;

-- :name delete-image :! :n
delete from images
 where id = :id
   and "user" = '00000000-0000-0000-0000-000000000000'::uuid;

-- :name create-image-collection
insert into images_collections (id, "user", name)
values (:id, '00000000-0000-0000-0000-000000000000'::uuid, :name)
    on conflict (id)
    do update set name = :name
returning *;

-- :name get-image
select * from images as i
 where i.id = :id
   and i."user" = '00000000-0000-0000-0000-000000000000'::uuid;
